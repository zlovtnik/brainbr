use axum::{
    extract::{Extension, Path, Query, State},
    response::IntoResponse,
    Json,
};
use serde::Deserialize;

use crate::api::middleware::{error::AppError, tenant::TenantId};
use crate::api::AppState;
use crate::services::transition::TransitionService;

pub async fn calendar(
    State(s): State<AppState>,
) -> Result<impl IntoResponse, AppError> {
    let result = TransitionService::calendar(&s.pool).await?;
    Ok(Json(result))
}

#[derive(Deserialize)]
pub struct EffectiveRateQuery {
    pub year: i32,
}

pub async fn effective_rate(
    State(s): State<AppState>,
    Extension(tenant): Extension<TenantId>,
    Path(sku_id): Path<String>,
    Query(q): Query<EffectiveRateQuery>,
) -> Result<impl IntoResponse, AppError> {
    let result = TransitionService::effective_rate(&s.pool, tenant.0, &sku_id, q.year).await?;
    Ok(Json(result))
}

pub async fn forecast(
    State(s): State<AppState>,
    Extension(tenant): Extension<TenantId>,
    Path(sku_id): Path<String>,
) -> Result<impl IntoResponse, AppError> {
    let result = TransitionService::forecast(&s.pool, tenant.0, &sku_id).await?;
    Ok(Json(result))
}
