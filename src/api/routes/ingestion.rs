use axum::{
    extract::{Extension, State},
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use sqlx::PgPool;
use std::sync::Arc;

use crate::api::middleware::{error::AppError, tenant::TenantId};
use crate::services::pipeline::IngestionService;

pub async fn create_job(
    State(pool): State<Arc<PgPool>>,
    Extension(tenant): Extension<TenantId>,
    Extension(request_id): Extension<Option<String>>,
    Json(body): Json<serde_json::Value>,
) -> Result<impl IntoResponse, AppError> {
    let result = IngestionService::enqueue(&pool, tenant.0, body, request_id.as_deref()).await?;
    Ok((StatusCode::ACCEPTED, Json(result)))
}
