use sqlx::PgPool;
use uuid::Uuid;

use crate::api::middleware::error::AppError;
use crate::api::middleware::tenant::set_rls_session;

pub struct AuditLogService;

impl AuditLogService {
    pub async fn append(
        pool: &PgPool,
        company_id: Uuid,
        sku_id: &str,
        event_type: &str,
        actor: Option<&str>,
        request_id: Option<&str>,
        payload: &serde_json::Value,
        run_id: Option<Uuid>,
        artifact_version: Option<&str>,
        artifact_digest: Option<&str>,
    ) -> Result<(), AppError> {
        let mut tx = pool.begin().await?;
        set_rls_session(&mut tx, company_id).await.map_err(AppError::Database)?;

        sqlx::query(
            r#"INSERT INTO fiscal_audit_log
                (company_id, sku_id, event_type, actor, request_id, event_payload, run_id, artifact_version, artifact_digest)
               VALUES ($1, $2, $3, $4, $5, $6::jsonb, $7, $8, $9)"#
        )
        .bind(company_id).bind(sku_id).bind(event_type).bind(actor).bind(request_id)
        .bind(payload).bind(run_id).bind(artifact_version).bind(artifact_digest)
        .execute(&mut *tx).await?;

        tx.commit().await?;
        Ok(())
    }
}
