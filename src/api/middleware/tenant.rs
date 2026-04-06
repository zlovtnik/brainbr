use axum::{
    extract::{Request, State},
    http::StatusCode,
    middleware::Next,
    response::{IntoResponse, Response},
    Json,
};
use sqlx::PgPool;
use std::sync::Arc;
use uuid::Uuid;

use crate::api::middleware::auth::AuthenticatedClaims;

#[derive(Debug, Clone, Copy)]
pub struct TenantId(pub Uuid);

pub async fn tenant_middleware(
    State(pool): State<Arc<PgPool>>,
    mut req: Request,
    next: Next,
) -> Response {
    let requires_tenant = {
        let path = req.uri().path();
        path.starts_with("/api/v1/inventory")
            || path.starts_with("/api/v1/audit")
            || path.starts_with("/api/v1/split-payment")
            || path.starts_with("/api/v1/ingestion")
            || path.starts_with("/api/v1/transition/sku/")
    };

    if let Some(claims) = req.extensions().get::<AuthenticatedClaims>().cloned() {
        if let Some(claim_val) = &claims.tenant_claim {
            if let Some(company_id) = resolve_tenant(&pool, claim_val).await {
                req.extensions_mut().insert(TenantId(company_id));
            }
        }
    }

    if requires_tenant && req.extensions().get::<TenantId>().is_none() {
        let has_auth = req.extensions().get::<AuthenticatedClaims>().is_some();
        let (code, msg) = if has_auth {
            ("FORBIDDEN", "Missing or invalid tenant claim")
        } else {
            ("UNAUTHORIZED", "Missing or invalid credentials")
        };
        let status = if has_auth { StatusCode::FORBIDDEN } else { StatusCode::UNAUTHORIZED };
        return (status, Json(serde_json::json!({ "error_code": code, "message": msg }))).into_response();
    }

    next.run(req).await
}

/// Always validates the claim against the database — never trusts a bare UUID from the client.
async fn resolve_tenant(pool: &PgPool, claim: &str) -> Option<Uuid> {
    if let Ok(uuid) = claim.parse::<Uuid>() {
        // Claim looks like a UUID — verify it exists as a company id
        let row: Option<(Uuid,)> = sqlx::query_as("SELECT id FROM companies WHERE id = $1")
            .bind(uuid)
            .fetch_optional(pool)
            .await
            .map_err(|e| tracing::error!("Failed to resolve tenant by UUID: {e}"))
            .ok()
            .flatten();
        return row.map(|(id,)| id);
    }
    None
}

pub async fn set_rls_session(
    tx: &mut sqlx::Transaction<'_, sqlx::Postgres>,
    company_id: Uuid,
) -> Result<(), sqlx::Error> {
    sqlx::query(
        "SELECT set_config('app.current_company_id', $1, TRUE), set_config('app.bypass_rls', 'false', TRUE)"
    )
    .bind(company_id.to_string())
    .fetch_one(&mut **tx)
    .await?;
    Ok(())
}
