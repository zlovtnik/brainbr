pub mod audit_log;

use chrono::{DateTime, Utc};
use serde::Serialize;
use sqlx::{PgPool, Row};
use uuid::Uuid;

use crate::api::middleware::error::AppError;
use crate::api::middleware::tenant::set_rls_session;
use crate::config::ModelsConfig;
use crate::services::rag::RagService;
use crate::services::transition::math::compute_risk_score;

#[derive(Serialize)]
pub struct ReAuditResponse {
    pub job_id: String,
    pub status: String,
}

#[derive(Serialize)]
pub struct AuditExplainResponse {
    pub sku_id: String,
    pub reform_taxes: serde_json::Value,
    pub audit_confidence: f64,
    pub llm_model_used: String,
    pub source: AuditSource,
}

#[derive(Serialize)]
pub struct AuditSource {
    pub law_ref: String,
    pub content: String,
    pub source_url: String,
}

#[derive(Serialize)]
pub struct AuditExplainabilityArtifactResponse {
    pub run_id: String,
    pub sku_id: String,
    pub job_id: String,
    pub request_id: Option<String>,
    pub artifact_version: String,
    pub schema_version: String,
    pub artifact_digest: String,
    pub llm_model_used: String,
    pub vector_id: String,
    pub audit_confidence: f64,
    pub source: AuditSource,
    pub replay_context: serde_json::Value,
    pub rag_output: serde_json::Value,
    pub created_at: DateTime<Utc>,
}

#[derive(Serialize)]
pub struct AuditQueryResponse {
    pub results: Vec<AuditQueryResult>,
}

#[derive(Serialize)]
pub struct AuditQueryResult {
    pub id: String,
    pub title: String,
    pub content: String,
    pub metadata: serde_json::Value,
    pub score: f64,
}

pub struct AuditService;

impl AuditService {
    /// Full RAG audit loop: embed SKU → retrieve legislation → LLM → validate → persist.
    pub async fn process_audit_job(
        pool: &PgPool,
        cfg: &ModelsConfig,
        job: crate::services::pipeline::AuditJob,
    ) -> anyhow::Result<()> {
        // 1. Load SKU
        let sku = sqlx::query(
            "SELECT sku_id, description, ncm_code, origin_state, destination_state, legacy_taxes \
             FROM inventory_transition WHERE sku_id = $1 AND company_id = $2 AND is_active = TRUE",
        )
        .bind(&job.sku_id)
        .bind(job.company_id)
        .fetch_optional(pool)
        .await?
        .ok_or_else(|| anyhow::anyhow!("SKU {} not found", job.sku_id))?;

        let ncm_code: String = sku.get("ncm_code");
        let description: String = sku.get("description");
        let origin_state: String = sku.get("origin_state");
        let destination_state: String = sku.get("destination_state");
        let legacy_taxes: serde_json::Value = sku.get("legacy_taxes");

        // 2. RAG: embed → retrieve → LLM → validate
        let rag = RagService::audit(pool, cfg, job.company_id, &ncm_code, &description, &origin_state, &destination_state).await?;

        // 3. Compute risk score
        let legacy_sum: f64 = legacy_taxes.as_object()
            .map(|m| m.values().filter_map(|v| v.as_f64()).sum())
            .unwrap_or(0.0);
        let reform_sum: f64 = rag.reform_taxes.as_object()
            .map(|m| m.values().filter_map(|v| v.as_f64()).sum())
            .unwrap_or(0.0);
        let risk_score = compute_risk_score(legacy_sum, reform_sum, Some(rag.audit_confidence));

        // 4. Build explainability artifact
        let run_id = Uuid::new_v4();
        let job_id_uuid: Uuid = job.job_id.parse().unwrap_or_else(|_| Uuid::new_v4());
        let artifact_version = "1.0.0";
        let schema_version = "rag-output-v1";
        let source_snapshot = serde_json::json!({
            "law_ref": rag.top_chunk.law_ref,
            "content": rag.top_chunk.content,
            "source_url": rag.top_chunk.source_url,
        });
        let replay_context = serde_json::json!({
            "ncm_code": ncm_code,
            "description": description,
            "origin_state": origin_state,
            "destination_state": destination_state,
            "job_id": job.job_id,
            "attempt": job.attempt,
        });
        let rag_output = serde_json::json!({
            "reform_taxes": rag.reform_taxes,
            "audit_confidence": rag.audit_confidence,
            "llm_model_used": cfg.llm,
            "source": source_snapshot,
        });
        let artifact_digest = {
            use sha2::{Sha256, Digest};
            let raw = serde_json::to_string(&rag_output).unwrap_or_default();
            hex::encode(Sha256::digest(raw.as_bytes()))
        };

        // 5. Persist — all in one transaction with RLS
        let mut tx = pool.begin().await?;
        set_rls_session(&mut tx, job.company_id).await.map_err(|e| anyhow::anyhow!("{e:?}"))?;

        // Insert explainability run
        sqlx::query(
            r#"INSERT INTO audit_explainability_run
               (id, company_id, sku_id, job_id, request_id, artifact_version, schema_version,
                llm_model_used, vector_id, audit_confidence, source_snapshot, replay_context,
                rag_output, artifact_digest)
               VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11::jsonb,$12::jsonb,$13::jsonb,$14)"#,
        )
        .bind(run_id)
        .bind(job.company_id)
        .bind(&job.sku_id)
        .bind(job_id_uuid)
        .bind(&job.request_id)
        .bind(artifact_version)
        .bind(schema_version)
        .bind(&cfg.llm)
        .bind(rag.top_chunk.chunk_id)
        .bind(rag.audit_confidence)
        .bind(&source_snapshot)
        .bind(&replay_context)
        .bind(&rag_output)
        .bind(&artifact_digest)
        .execute(&mut *tx)
        .await?;

        // Update inventory_transition with RAG results
        sqlx::query(
            r#"UPDATE inventory_transition SET
               reform_taxes = $1::jsonb,
               vector_id = $2,
               audit_confidence = $3,
               llm_model_used = $4,
               last_llm_audit = NOW(),
               transition_risk_score = $5,
               updated_at = NOW()
               WHERE sku_id = $6 AND company_id = $7"#,
        )
        .bind(&rag.reform_taxes)
        .bind(rag.top_chunk.chunk_id)
        .bind(rag.audit_confidence)
        .bind(&cfg.llm)
        .bind(risk_score as i16)
        .bind(&job.sku_id)
        .bind(job.company_id)
        .execute(&mut *tx)
        .await?;

        // Emit RATE_GENERATED audit event
        sqlx::query(
            r#"INSERT INTO fiscal_audit_log
               (company_id, sku_id, event_type, actor, request_id, run_id, artifact_version, artifact_digest, event_payload)
               VALUES ($1,$2,'RATE_GENERATED','worker',$3,$4,$5,$6,$7::jsonb)"#,
        )
        .bind(job.company_id)
        .bind(&job.sku_id)
        .bind(&job.request_id)
        .bind(run_id)
        .bind(artifact_version)
        .bind(&artifact_digest)
        .bind(serde_json::json!({
            "job_id": job.job_id,
            "ncm_code": ncm_code,
            "audit_confidence": rag.audit_confidence,
            "risk_score": risk_score,
        }))
        .execute(&mut *tx)
        .await?;

        tx.commit().await?;
        tracing::info!(sku_id = %job.sku_id, ncm = %ncm_code, confidence = %rag.audit_confidence, "RAG audit complete");
        Ok(())
    }

    pub async fn enqueue_sku_audit(
        pool: &PgPool,
        company_id: Uuid,
        sku_id: &str,
        request_id: Option<&str>,
    ) -> Result<ReAuditResponse, AppError> {
        let mut tx = pool.begin().await?;
        set_rls_session(&mut tx, company_id).await.map_err(AppError::Database)?;

        let exists = sqlx::query(
            "SELECT 1 FROM inventory_transition WHERE company_id=$1 AND sku_id=$2 AND is_active=TRUE"
        )
        .bind(company_id).bind(sku_id)
        .fetch_optional(&mut *tx).await?;

        if exists.is_none() {
            tx.rollback().await?;
            return Err(AppError::NotFound(format!("SKU {sku_id} not found")));
        }

        let job_id = Uuid::new_v4();
        sqlx::query(
            r#"INSERT INTO fiscal_audit_log (company_id, sku_id, event_type, actor, request_id, event_payload)
               VALUES ($1, $2, 'RE_AUDIT_QUEUED', 'api', $3, $4::jsonb)"#
        )
        .bind(company_id).bind(sku_id).bind(request_id)
        .bind(serde_json::json!({ "job_id": job_id, "status": "queued" }))
        .execute(&mut *tx).await?;

        tx.commit().await?;
        Ok(ReAuditResponse { job_id: job_id.to_string(), status: "queued".into() })
    }

    pub async fn explain(pool: &PgPool, company_id: Uuid, sku_id: &str) -> Result<AuditExplainResponse, AppError> {
        let mut tx = pool.begin().await?;
        set_rls_session(&mut tx, company_id).await.map_err(AppError::Database)?;

        let r = sqlx::query(
            r#"SELECT i.sku_id, i.reform_taxes, i.audit_confidence, i.llm_model_used,
                      c.id AS chunk_id, c.content AS chunk_content,
                      b.law_ref, COALESCE(b.source_url, '') AS source_url
               FROM inventory_transition i
               LEFT JOIN fiscal_knowledge_chunk c ON c.id = i.vector_id
               LEFT JOIN fiscal_knowledge_base b ON b.id = c.knowledge_id
               WHERE i.company_id = $1 AND i.sku_id = $2 AND i.is_active = TRUE"#
        )
        .bind(company_id).bind(sku_id)
        .fetch_optional(&mut *tx).await?
        .ok_or_else(|| AppError::NotFound(format!("SKU {sku_id} not found")))?;

        tx.commit().await?;

        let chunk_id: Option<Uuid> = r.get("chunk_id");
        if chunk_id.is_none() {
            return Err(AppError::NotFound(format!("No audit result found for SKU {sku_id}")));
        }

        Ok(AuditExplainResponse {
            sku_id: r.get("sku_id"),
            reform_taxes: r.get("reform_taxes"),
            audit_confidence: r.get::<Option<f64>, _>("audit_confidence").unwrap_or(0.0),
            llm_model_used: r.get::<Option<String>, _>("llm_model_used").unwrap_or_default(),
            source: AuditSource {
                law_ref: r.get::<Option<String>, _>("law_ref").unwrap_or_default(),
                content: r.get::<Option<String>, _>("chunk_content").unwrap_or_default(),
                source_url: r.get("source_url"),
            },
        })
    }

    pub async fn explain_latest_artifact(pool: &PgPool, company_id: Uuid, sku_id: &str) -> Result<AuditExplainabilityArtifactResponse, AppError> {
        let mut tx = pool.begin().await?;
        set_rls_session(&mut tx, company_id).await.map_err(AppError::Database)?;

        let r = sqlx::query(
            r#"SELECT id, sku_id, job_id, request_id, artifact_version, schema_version, llm_model_used,
                      vector_id, audit_confidence, source_snapshot, replay_context, rag_output, artifact_digest, created_at
               FROM audit_explainability_run
               WHERE company_id = $1 AND sku_id = $2
               ORDER BY created_at DESC LIMIT 1"#
        )
        .bind(company_id).bind(sku_id)
        .fetch_optional(&mut *tx).await?
        .ok_or_else(|| AppError::NotFound(format!("No explainability artifact found for SKU {sku_id}")))?;

        tx.commit().await?;
        Ok(row_to_artifact(&r))
    }

    pub async fn explain_artifact_by_run_id(pool: &PgPool, company_id: Uuid, run_id: &str) -> Result<AuditExplainabilityArtifactResponse, AppError> {
        let run_uuid = run_id.parse::<Uuid>().map_err(|_| AppError::BadRequest("Invalid run_id format".into()))?;
        let mut tx = pool.begin().await?;
        set_rls_session(&mut tx, company_id).await.map_err(AppError::Database)?;

        let r = sqlx::query(
            r#"SELECT id, sku_id, job_id, request_id, artifact_version, schema_version, llm_model_used,
                      vector_id, audit_confidence, source_snapshot, replay_context, rag_output, artifact_digest, created_at
               FROM audit_explainability_run
               WHERE company_id = $1 AND id = $2 LIMIT 1"#
        )
        .bind(company_id).bind(run_uuid)
        .fetch_optional(&mut *tx).await?
        .ok_or_else(|| AppError::NotFound(format!("Explainability run {run_id} not found")))?;

        tx.commit().await?;
        Ok(row_to_artifact(&r))
    }

    /// Semantic search over the knowledge base using vector similarity.
    pub async fn query(
        pool: &PgPool,
        cfg: &ModelsConfig,
        company_id: Uuid,
        query: &str,
        k: i64,
        filters: Option<serde_json::Value>,
    ) -> Result<AuditQueryResponse, AppError> {
        let state_filter = filters.as_ref()
            .and_then(|f| f["state"].as_str())
            .map(String::from);

        let embedding = RagService::embed(cfg, query).await
            .map_err(|e| AppError::Internal(anyhow::anyhow!("embedding failed: {e}")))?;

        let chunks = RagService::vector_search(pool, company_id, &embedding, k, state_filter.as_deref()).await
            .map_err(|e| AppError::Internal(anyhow::anyhow!("vector search failed: {e}")))?;

        let results = chunks.into_iter().map(|c| AuditQueryResult {
            id: c.chunk_id.to_string(),
            title: c.law_ref,
            content: c.content,
            metadata: serde_json::json!({ "source_url": c.source_url, "knowledge_id": c.knowledge_id }),
            score: c.score,
        }).collect();

        Ok(AuditQueryResponse { results })
    }
}

fn row_to_artifact(r: &sqlx::postgres::PgRow) -> AuditExplainabilityArtifactResponse {
    let source_snapshot: serde_json::Value = r.get("source_snapshot");
    AuditExplainabilityArtifactResponse {
        run_id: r.get::<Uuid, _>("id").to_string(),
        sku_id: r.get("sku_id"),
        job_id: r.get::<Uuid, _>("job_id").to_string(),
        request_id: r.get("request_id"),
        artifact_version: r.get::<Option<String>, _>("artifact_version").unwrap_or_default(),
        schema_version: r.get::<Option<String>, _>("schema_version").unwrap_or_default(),
        artifact_digest: r.get::<Option<String>, _>("artifact_digest").unwrap_or_default(),
        llm_model_used: r.get::<Option<String>, _>("llm_model_used").unwrap_or_default(),
        vector_id: r.get::<Option<Uuid>, _>("vector_id").map(|u| u.to_string()).unwrap_or_default(),
        audit_confidence: r.get::<Option<f64>, _>("audit_confidence").unwrap_or(0.0),
        source: AuditSource {
            law_ref: source_snapshot["law_ref"].as_str().unwrap_or("").into(),
            content: source_snapshot["content"].as_str().unwrap_or("").into(),
            source_url: source_snapshot["source_url"].as_str().unwrap_or("").into(),
        },
        replay_context: r.get("replay_context"),
        rag_output: r.get("rag_output"),
        created_at: r.get("created_at"),
    }
}
