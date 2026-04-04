use axum::{
    extract::{Extension, Path, Query, State},
    response::IntoResponse,
    Json,
};
use serde::Deserialize;
use sqlx::PgPool;
use std::sync::Arc;

use crate::api::middleware::{error::AppError, tenant::TenantId};
use crate::services::transition::TransitionService;

pub async fn calendar(
    State(pool): State<Arc<PgPool>>,
) -> Result<impl IntoResponse, AppError> {
    let result = TransitionService::calendar(&pool).await?;
    Ok(Json(result))
}

#[derive(Deserialize)]
pub struct EffectiveRateQuery {
    pub year: i32,
}

pub async fn effective_rate(
    State(pool): State<Arc<PgPool>>,
    Extension(tenant): Extension<TenantId>,
    Path(sku_id): Path<String>,
    Query(q): Query<EffectiveRateQuery>,
) -> Result<impl IntoResponse, AppError> {
    let result = TransitionService::effective_rate(&pool, tenant.0, &sku_id, q.year).await?;
    Ok(Json(result))
}
