use axum::{
    extract::{Extension, Path, Query, State},
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use serde::Deserialize;
use sqlx::PgPool;
use std::sync::Arc;

use crate::api::middleware::{error::AppError, tenant::TenantId};
use crate::services::audit::AuditService;

pub async fn re_audit(
    State(pool): State<Arc<PgPool>>,
    Extension(tenant): Extension<TenantId>,
    Path(sku_id): Path<String>,
    Extension(request_id): Extension<Option<String>>,
) -> Result<impl IntoResponse, AppError> {
    let result = AuditService::enqueue_sku_audit(&pool, tenant.0, &sku_id, request_id.as_deref()).await?;
    Ok((StatusCode::ACCEPTED, Json(result)))
}

pub async fn explain(
    State(pool): State<Arc<PgPool>>,
    Extension(tenant): Extension<TenantId>,
    Path(sku_id): Path<String>,
) -> Result<impl IntoResponse, AppError> {
    let result = AuditService::explain(&pool, tenant.0, &sku_id).await?;
    Ok(Json(result))
}

pub async fn explain_latest_artifact(
    State(pool): State<Arc<PgPool>>,
    Extension(tenant): Extension<TenantId>,
    Path(sku_id): Path<String>,
) -> Result<impl IntoResponse, AppError> {
    let result = AuditService::explain_latest_artifact(&pool, tenant.0, &sku_id).await?;
    Ok(Json(result))
}

pub async fn explain_artifact_by_run(
    State(pool): State<Arc<PgPool>>,
    Extension(tenant): Extension<TenantId>,
    Path(run_id): Path<String>,
) -> Result<impl IntoResponse, AppError> {
    let result = AuditService::explain_artifact_by_run_id(&pool, tenant.0, &run_id).await?;
    Ok(Json(result))
}

#[derive(Deserialize)]
pub struct QueryRequest {
    pub query: String,
    #[serde(default = "default_k")]
    pub k: i64,
    pub filters: Option<serde_json::Value>,
}
fn default_k() -> i64 { 5 }

pub async fn query(
    State(pool): State<Arc<PgPool>>,
    Extension(tenant): Extension<TenantId>,
    Json(body): Json<QueryRequest>,
) -> Result<impl IntoResponse, AppError> {
    let result = AuditService::query(&pool, tenant.0, &body.query, body.k, body.filters).await?;
    Ok(Json(result))
}
