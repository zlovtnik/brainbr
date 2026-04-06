use axum::{
    extract::{Extension, Path, Query, State},
    http::StatusCode,
    response::IntoResponse,
    Json,
};
use serde::Deserialize;

use crate::api::middleware::{error::AppError, tenant::TenantId};
use crate::api::AppState;
use crate::services::inventory::{InventoryListFilters, InventoryService};

#[derive(Deserialize)]
pub struct ListQuery {
    #[serde(default = "default_page")]
    pub page: i64,
    #[serde(default = "default_limit")]
    pub limit: i64,
    #[serde(default)]
    pub include_inactive: bool,
    pub query: Option<String>,
    pub sort_by: Option<String>,
    pub sort_order: Option<String>,
}
fn default_page() -> i64 { 1 }
fn default_limit() -> i64 { 50 }

pub async fn list(
    State(s): State<AppState>,
    Extension(tenant): Extension<TenantId>,
    Query(q): Query<ListQuery>,
) -> Result<impl IntoResponse, AppError> {
    let filters = InventoryListFilters::parse(q.page, q.limit, q.include_inactive, q.query, q.sort_by, q.sort_order)?;
    let result = InventoryService::list(&s.pool, tenant.0, filters).await?;
    Ok(Json(result))
}

pub async fn get(
    State(s): State<AppState>,
    Extension(tenant): Extension<TenantId>,
    Path(sku_id): Path<String>,
) -> Result<impl IntoResponse, AppError> {
    let result = InventoryService::get(&s.pool, tenant.0, &sku_id, false).await?;
    Ok(Json(result))
}

pub async fn upsert(
    State(s): State<AppState>,
    Extension(tenant): Extension<TenantId>,
    Json(body): Json<serde_json::Value>,
) -> Result<impl IntoResponse, AppError> {
    let result = InventoryService::upsert(&s.pool, tenant.0, body).await?;
    Ok((StatusCode::OK, Json(result)))
}

pub async fn update(
    State(s): State<AppState>,
    Extension(tenant): Extension<TenantId>,
    Path(sku_id): Path<String>,
    Json(body): Json<serde_json::Value>,
) -> Result<impl IntoResponse, AppError> {
    let result = InventoryService::update(&s.pool, tenant.0, &sku_id, body).await?;
    Ok(Json(result))
}

pub async fn delete(
    State(s): State<AppState>,
    Extension(tenant): Extension<TenantId>,
    Path(sku_id): Path<String>,
) -> Result<impl IntoResponse, AppError> {
    let result = InventoryService::delete(&s.pool, tenant.0, &sku_id).await?;
    Ok(Json(result))
}
