use axum::{
    extract::{Extension, Query, State},
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use serde::Deserialize;
use sqlx::PgPool;
use std::sync::Arc;

use crate::api::middleware::{error::AppError, tenant::TenantId};
use crate::services::splitpayment::SplitPaymentService;

#[derive(Deserialize)]
pub struct ListQuery {
    #[serde(default = "default_page")]
    page: i64,
    #[serde(default = "default_limit")]
    limit: i64,
    pub sku_id: Option<String>,
    pub event_type: Option<String>,
}
fn default_page() -> i64 { 1 }
fn default_limit() -> i64 { 50 }

const MAX_LIMIT: i64 = 100;

impl ListQuery {
    fn page(&self) -> i64 { self.page.max(1) }
    fn limit(&self) -> i64 { self.limit.clamp(1, MAX_LIMIT) }
}

pub async fn create(
    State(pool): State<Arc<PgPool>>,
    Extension(tenant): Extension<TenantId>,
    Extension(request_id): Extension<Option<String>>,
    Json(body): Json<serde_json::Value>,
) -> Result<impl IntoResponse, AppError> {
    let result = SplitPaymentService::create(&pool, tenant.0, body, request_id.as_deref()).await?;
    Ok((StatusCode::CREATED, Json(result)))
}

pub async fn list(
    State(pool): State<Arc<PgPool>>,
    Extension(tenant): Extension<TenantId>,
    Query(q): Query<ListQuery>,
) -> Result<impl IntoResponse, AppError> {
    let result = SplitPaymentService::list(&pool, tenant.0, q.page(), q.limit(), q.sku_id, q.event_type).await?;
    Ok(Json(result))
}
