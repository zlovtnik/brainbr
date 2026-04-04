pub mod audit_log;

use chrono::{DateTime, Utc};
use serde::Serialize;
use sqlx::{PgPool, Row};
use uuid::Uuid;

use crate::api::middleware::error::AppError;
use crate::api::middleware::tenant::set_rls_session;

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

    pub async fn query(
        _pool: &PgPool,
        _company_id: Uuid,
        _query: &str,
        _k: i64,
        _filters: Option<serde_json::Value>,
    ) -> Result<AuditQueryResponse, AppError> {
        // Embedding + vector search wired in Phase 4 (pipeline module)
        Ok(AuditQueryResponse { results: vec![] })
    }
}

fn row_to_artifact(r: &sqlx::postgres::PgRow) -> AuditExplainabilityArtifactResponse {
    let source_snapshot: serde_json::Value = r.get("source_snapshot");
    AuditExplainabilityArtifactResponse {
        run_id: r.get::<Uuid, _>("id").to_string(),
        sku_id: r.get("sku_id"),
        job_id: r.get("job_id"),
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
