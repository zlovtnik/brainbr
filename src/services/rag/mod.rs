use anyhow::{anyhow, Context};
use reqwest::Client;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use sqlx::{PgPool, Row};
use uuid::Uuid;

use crate::config::ModelsConfig;

// ── OpenAI wire types ────────────────────────────────────────────────────────

#[derive(Serialize)]
struct EmbedRequest<'a> {
    model: &'a str,
    input: Vec<&'a str>,
}

#[derive(Deserialize)]
struct EmbedResponse {
    data: Vec<EmbedData>,
}

#[derive(Deserialize)]
struct EmbedData {
    embedding: Vec<f32>,
}

#[derive(Serialize)]
struct ChatRequest<'a> {
    model: &'a str,
    messages: Vec<ChatMessage>,
    response_format: ResponseFormat,
    temperature: f32,
}

#[derive(Serialize)]
struct ChatMessage {
    role: String,
    content: String,
}

#[derive(Serialize)]
struct ResponseFormat {
    r#type: &'static str,
}

#[derive(Deserialize)]
struct ChatResponse {
    choices: Vec<ChatChoice>,
}

#[derive(Deserialize)]
struct ChatChoice {
    message: ChatChoiceMessage,
}

#[derive(Deserialize)]
struct ChatChoiceMessage {
    content: String,
}

// ── Public types ─────────────────────────────────────────────────────────────

pub struct RetrievedChunk {
    pub chunk_id: Uuid,
    pub knowledge_id: Uuid,
    pub law_ref: String,
    pub source_url: String,
    pub content: String,
    pub score: f64,
}

pub struct RagResult {
    pub reform_taxes: Value,
    pub audit_confidence: f64,
    pub top_chunk: RetrievedChunk,
    pub raw_output: Value,
}

// ── RagService ────────────────────────────────────────────────────────────────

pub struct RagService;

impl RagService {
    /// Embed a single text string via OpenAI.
    pub async fn embed(cfg: &ModelsConfig, text: &str) -> anyhow::Result<Vec<f32>> {
        if cfg.provider_mode == "mock" {
            return Ok(vec![0.0f32; 1536]);
        }
        let resp: EmbedResponse = Client::new()
            .post(format!("{}/embeddings", cfg.openai_base_url))
            .bearer_auth(&cfg.openai_api_key)
            .json(&EmbedRequest { model: &cfg.embedding, input: vec![text] })
            .send().await.context("embedding request")?
            .error_for_status().context("embedding API error")?
            .json().await.context("embedding parse")?;
        resp.data.into_iter().next()
            .map(|d| d.embedding)
            .ok_or_else(|| anyhow!("empty embedding response"))
    }

    /// Embed a batch of texts.
    pub async fn embed_batch(cfg: &ModelsConfig, texts: &[&str]) -> anyhow::Result<Vec<Vec<f32>>> {
        if cfg.provider_mode == "mock" {
            return Ok(texts.iter().map(|_| vec![0.0f32; 1536]).collect());
        }
        let resp: EmbedResponse = Client::new()
            .post(format!("{}/embeddings", cfg.openai_base_url))
            .bearer_auth(&cfg.openai_api_key)
            .json(&EmbedRequest { model: &cfg.embedding, input: texts.to_vec() })
            .send().await.context("batch embedding request")?
            .error_for_status().context("batch embedding API error")?
            .json().await.context("batch embedding parse")?;
        Ok(resp.data.into_iter().map(|d| d.embedding).collect())
    }

    /// Top-K cosine similarity search over fiscal_knowledge_chunk.
    /// Searches shared (company_id IS NULL) and tenant-owned chunks.
    pub async fn vector_search(
        pool: &PgPool,
        company_id: Uuid,
        embedding: &[f32],
        k: i64,
        state_filter: Option<&str>,
    ) -> anyhow::Result<Vec<RetrievedChunk>> {
        let vec_literal = to_vector_literal(embedding);
        let rows = sqlx::query(
            r#"SELECT c.id AS chunk_id, c.knowledge_id, c.content,
                      COALESCE(b.law_ref, '') AS law_ref,
                      COALESCE(b.source_url, '') AS source_url,
                      1 - (c.embedding <=> $1::vector) AS score
               FROM fiscal_knowledge_chunk c
               JOIN fiscal_knowledge_base b ON b.id = c.knowledge_id
               WHERE c.embedding IS NOT NULL
                 AND (c.company_id = $2 OR b.company_id IS NULL)
                 AND b.is_superseded = FALSE
                 AND ($3::text IS NULL OR b.metadata->>'state' = $3)
               ORDER BY c.embedding <=> $1::vector
               LIMIT $4"#,
        )
        .bind(vec_literal)
        .bind(company_id)
        .bind(state_filter)
        .bind(k)
        .fetch_all(pool).await.context("vector search")?;

        Ok(rows.into_iter().map(|r| RetrievedChunk {
            chunk_id: r.get("chunk_id"),
            knowledge_id: r.get("knowledge_id"),
            law_ref: r.get("law_ref"),
            source_url: r.get("source_url"),
            content: r.get("content"),
            score: r.get::<f64, _>("score"),
        }).collect())
    }

    /// Full RAG audit: embed → retrieve → prompt → LLM → validate.
    pub async fn audit(
        pool: &PgPool,
        cfg: &ModelsConfig,
        company_id: Uuid,
        ncm_code: &str,
        description: &str,
        origin_state: &str,
        destination_state: &str,
    ) -> anyhow::Result<RagResult> {
        let query_text = format!(
            "NCM {} - {} - origem {} destino {}",
            ncm_code, description, origin_state, destination_state
        );
        let embedding = Self::embed(cfg, &query_text).await?;

        // Try state-scoped retrieval first, fall back to global
        let mut chunks = Self::vector_search(pool, company_id, &embedding, 5, Some(origin_state)).await?;
        if chunks.is_empty() {
            chunks = Self::vector_search(pool, company_id, &embedding, 5, None).await?;
        }
        if chunks.is_empty() {
            return Err(anyhow!("No legislation found in knowledge base for NCM {ncm_code}"));
        }

        let top_score = chunks[0].score;
        let context = chunks.iter().enumerate()
            .map(|(i, c)| format!("[{}] {} ({})\n{}", i + 1, c.law_ref, c.source_url, c.content))
            .collect::<Vec<_>>().join("\n\n---\n\n");

        let prompt = build_prompt(ncm_code, description, origin_state, destination_state, &context);
        let raw_output = if cfg.provider_mode == "mock" {
            mock_llm_response(ncm_code)
        } else {
            call_llm(cfg, &prompt).await?
        };

        let reform_taxes = validate_reform_taxes(&raw_output)?;
        let llm_confidence = reform_taxes["confidence"].as_f64().unwrap_or(0.5);
        let audit_confidence = (top_score * 0.8 + llm_confidence * 0.2).clamp(0.0, 1.0);

        let top_chunk = chunks.into_iter().next().unwrap();
        Ok(RagResult { reform_taxes, audit_confidence, top_chunk, raw_output })
    }
}

// ── Prompt ────────────────────────────────────────────────────────────────────

fn build_prompt(ncm: &str, desc: &str, origin: &str, dest: &str, context: &str) -> String {
    format!(
        r#"Você é um especialista em tributação brasileira (Reforma Tributária EC 132/2023, LC 68/2024).

Produto: NCM {ncm} — {desc}
Operação: {origin} → {dest}

Legislação recuperada:
{context}

Com base exclusivamente na legislação acima, retorne um JSON com exatamente estas chaves:
{{
  "ibs": <alíquota IBS decimal, ex: 0.175>,
  "cbs": <alíquota CBS decimal>,
  "tax_rate": <total IBS+CBS>,
  "is_taxable": <true|false>,
  "cashback_eligible": <true|false>,
  "regime": "<geral|monofásico|isento|reduzido>",
  "reduced_basket": <true|false>,
  "confidence": <0.0–1.0>
}}

Responda APENAS com o JSON, sem texto adicional."#
    )
}

// ── LLM call ──────────────────────────────────────────────────────────────────

async fn call_llm(cfg: &ModelsConfig, prompt: &str) -> anyhow::Result<Value> {
    let resp: ChatResponse = Client::new()
        .post(format!("{}/chat/completions", cfg.openai_base_url))
        .bearer_auth(&cfg.openai_api_key)
        .json(&ChatRequest {
            model: &cfg.llm,
            messages: vec![ChatMessage { role: "user".into(), content: prompt.into() }],
            response_format: ResponseFormat { r#type: "json_object" },
            temperature: 0.0,
        })
        .send().await.context("LLM request")?
        .error_for_status().context("LLM API error")?
        .json().await.context("LLM response parse")?;

    let content = resp.choices.into_iter().next()
        .ok_or_else(|| anyhow!("empty LLM response"))?
        .message.content;
    serde_json::from_str(&content).context("LLM output is not valid JSON")
}

fn mock_llm_response(ncm: &str) -> Value {
    serde_json::json!({
        "ibs": 0.175, "cbs": 0.088, "tax_rate": 0.263,
        "is_taxable": true, "cashback_eligible": false,
        "regime": "geral", "reduced_basket": false,
        "confidence": 0.75, "_mock": true, "_ncm": ncm
    })
}

// ── Schema validation ─────────────────────────────────────────────────────────

fn validate_reform_taxes(v: &Value) -> anyhow::Result<Value> {
    let obj = v.as_object().ok_or_else(|| anyhow!("LLM output is not a JSON object"))?;
    for key in &["ibs", "cbs", "tax_rate", "is_taxable"] {
        if !obj.contains_key(*key) {
            return Err(anyhow!("LLM output missing required field: {key}"));
        }
    }
    for key in &["ibs", "cbs", "tax_rate"] {
        let val = obj[*key].as_f64().ok_or_else(|| anyhow!("field {key} must be a number"))?;
        if val < 0.0 { return Err(anyhow!("field {key} must be >= 0")); }
    }
    if obj["is_taxable"].as_bool().is_none() {
        return Err(anyhow!("field is_taxable must be a boolean"));
    }
    let allowed = ["ibs", "cbs", "tax_rate", "is_taxable", "cashback_eligible", "regime", "reduced_basket", "confidence"];
    let clean: serde_json::Map<String, Value> = obj.iter()
        .filter(|(k, _)| allowed.contains(&k.as_str()))
        .map(|(k, v)| (k.clone(), v.clone()))
        .collect();
    Ok(Value::Object(clean))
}

// ── Helpers ───────────────────────────────────────────────────────────────────

fn to_vector_literal(values: &[f32]) -> String {
    let inner = values.iter().map(|v| format!("{v:.8}")).collect::<Vec<_>>().join(",");
    format!("[{inner}]")
}
