use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::IntoResponse,
    Json,
};

use crate::api::middleware::{auth::AuthenticatedClaims, error::AppError, tenant::TenantId};
use crate::api::AppState;
use crate::services::pipeline::IngestionService;

pub async fn create_job(
    State(s): State<AppState>,
    Extension(tenant): Extension<TenantId>,
    Extension(claims): Extension<AuthenticatedClaims>,
    Extension(request_id): Extension<Option<String>>,
    Json(body): Json<serde_json::Value>,
) -> Result<impl IntoResponse, AppError> {
    let result = IngestionService::enqueue(
        &s.pool,
        s.config.as_ref(),
        tenant.0,
        Some(claims.sub.as_str()),
        body,
        request_id.as_deref(),
    )
    .await?;
    Ok((StatusCode::ACCEPTED, Json(result)))
}
