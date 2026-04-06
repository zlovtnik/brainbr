use anyhow::{anyhow, Context};
use once_cell::sync::Lazy;
use reqwest::Client;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use sqlx::{PgPool, Row};
use std::time::Duration;
use uuid::Uuid;

use crate::config::ModelsConfig;

static HTTP_CLIENT: Lazy<Client> = Lazy::new(|| {
    Client::builder()
        .timeout(Duration::from_secs(30))
        .build()
        .expect("failed to build HTTP client")
});

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
        let resp: EmbedResponse = HTTP_CLIENT
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
        let resp: EmbedResponse = HTTP_CLIENT
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
            .map(|(i, c)| format!("[{}] {} ({})\n{}", i + 1, sanitize_input(&c.law_ref), sanitize_input(&c.source_url), sanitize_context(&c.content)))
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

fn sanitize_input(s: &str) -> String {
    s.chars()
        .filter(|c| !matches!(c, '\x00'..='\x08' | '\x0b' | '\x0c' | '\x0e'..='\x1f' | '\x7f'))
        .map(|c| match c {
            '{' => '(', '}' => ')', '"' => '\'', '\n' | '\r' => ' ',
            c => c,
        })
        .take(500)
        .collect()
}

/// Like `sanitize_input` but preserves newlines (legislation context is multi-line)
/// and has a higher char limit. Still strips control chars and brace injection.
fn sanitize_context(s: &str) -> String {
    s.chars()
        .filter(|c| !matches!(c, '\x00'..='\x08' | '\x0b' | '\x0c' | '\x0e'..='\x1f' | '\x7f'))
        .map(|c| match c {
            '{' => '(', '}' => ')', '"' => '\'',
            c => c,
        })
        .take(4000)
        .collect()
}

fn build_prompt(ncm: &str, desc: &str, origin: &str, dest: &str, context: &str) -> String {
    let (ncm, desc, origin, dest) = (
        sanitize_input(ncm),
        sanitize_input(desc),
        sanitize_input(origin),
        sanitize_input(dest),
    );
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
    let resp: ChatResponse = HTTP_CLIENT
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

#[cfg(test)]
mod tests {
    use super::*;
    use proptest::prelude::*;

    // ── deterministic unit tests ──────────────────────────────────────────────

    #[test]
    fn strips_null_bytes() {
        assert!(!sanitize_input("foo\x00bar").contains('\x00'));
    }

    #[test]
    fn replaces_braces() {
        let out = sanitize_input("{\"inject\": \"payload\"}");
        assert!(!out.contains('{'));
        assert!(!out.contains('}'));
        assert!(!out.contains('"'));
    }

    #[test]
    fn collapses_newlines_in_input() {
        let out = sanitize_input("line1\nline2\r\nline3");
        assert!(!out.contains('\n'));
        assert!(!out.contains('\r'));
    }

    #[test]
    fn context_preserves_newlines_but_strips_braces() {
        let out = sanitize_context("Art. 1\n{inject}\nArt. 2");
        assert!(out.contains('\n'));
        assert!(!out.contains('{'));
        assert!(!out.contains('}'));
    }

    #[test]
    fn truncates_at_limit() {
        let long = "a".repeat(1000);
        assert_eq!(sanitize_input(&long).len(), 500);
        let long_ctx = "a".repeat(5000);
        assert_eq!(sanitize_context(&long_ctx).len(), 4000);
    }

    #[test]
    fn build_prompt_no_raw_braces_in_user_fields() {
        let prompt = build_prompt(
            "9999.99{inject}",
            "desc} evil {payload",
            "SP{x}",
            "RJ\x01",
            "Art. 1\nlegislação normal",
        );
        // The JSON template braces in the prompt are expected; only user-field
        // injections must be absent. Check the user-data lines specifically.
        let produto_line = prompt.lines().find(|l| l.starts_with("Produto:")).unwrap();
        let operacao_line = prompt.lines().find(|l| l.starts_with("Operação:")).unwrap();
        assert!(!produto_line.contains('{'));
        assert!(!produto_line.contains('}'));
        assert!(!operacao_line.contains('{'));
        assert!(!operacao_line.contains('}'));
    }

    // ── proptest fuzz ─────────────────────────────────────────────────────────

    proptest! {
        #[test]
        fn fuzz_sanitize_input_no_control_chars(s in ".*") {
            let out = sanitize_input(&s);
            prop_assert!(out.len() <= 500);
            let has_control = out.chars().any(|c| matches!(c,
                '\x00'..='\x08' | '\x0b' | '\x0c' | '\x0e'..='\x1f' | '\x7f'
            ));
            prop_assert!(!has_control);
            let has_open_brace = out.contains('{');
            let has_close_brace = out.contains('}');
            let has_dquote = out.contains('"');
            prop_assert!(!has_open_brace);
            prop_assert!(!has_close_brace);
            prop_assert!(!has_dquote);
        }

        #[test]
        fn fuzz_sanitize_context_no_control_chars(s in ".*") {
            let out = sanitize_context(&s);
            prop_assert!(out.len() <= 4000);
            let has_control = out.chars().any(|c| matches!(c,
                '\x00'..='\x08' | '\x0b' | '\x0c' | '\x0e'..='\x1f' | '\x7f'
            ));
            prop_assert!(!has_control);
            let has_open_brace = out.contains('{');
            let has_close_brace = out.contains('}');
            prop_assert!(!has_open_brace);
            prop_assert!(!has_close_brace);
        }

        #[test]
        fn fuzz_build_prompt_user_fields_clean(
            ncm in "[0-9A-Za-z.{}\"\x00-\x1f]{0,20}",
            desc in ".{0,100}",
            origin in ".{0,10}",
            dest in ".{0,10}",
        ) {
            let prompt = build_prompt(&ncm, &desc, &origin, &dest, "legislação");
            let produto = prompt.lines().find(|l| l.starts_with("Produto:")).unwrap_or("");
            let operacao = prompt.lines().find(|l| l.starts_with("Operação:")).unwrap_or("");
            let produto_clean = !produto.contains('{') && !produto.contains('}');
            let operacao_clean = !operacao.contains('{') && !operacao.contains('}');
            prop_assert!(produto_clean);
            prop_assert!(operacao_clean);
        }
    }
}
