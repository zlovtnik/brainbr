use axum::{extract::State, http::StatusCode, response::IntoResponse, Json};
use serde::Serialize;
use sqlx::PgPool;
use std::sync::Arc;

#[derive(Debug, Clone, PartialEq, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum ComponentStatus { Healthy, Degraded, Unhealthy }

#[derive(Clone)]
pub struct HealthRegistry {
    pool: Arc<PgPool>,
}

impl HealthRegistry {
    pub fn new(pool: Arc<PgPool>) -> Self { Self { pool } }

    pub async fn check_postgres(&self) -> ComponentStatus {
        match sqlx::query("SELECT 1").fetch_one(self.pool.as_ref()).await {
            Ok(_) => ComponentStatus::Healthy,
            Err(_) => ComponentStatus::Unhealthy,
        }
    }
}

pub async fn health() -> impl IntoResponse {
    (StatusCode::OK, Json(serde_json::json!({ "status": "UP" })))
}

pub async fn liveness() -> impl IntoResponse {
    (StatusCode::OK, Json(serde_json::json!({ "status": "UP" })))
}

pub async fn readiness(State(registry): State<Arc<HealthRegistry>>) -> impl IntoResponse {
    let postgres = registry.check_postgres().await;
    let overall = if postgres == ComponentStatus::Healthy { "UP" } else { "DOWN" };
    let status = if postgres == ComponentStatus::Healthy { StatusCode::OK } else { StatusCode::SERVICE_UNAVAILABLE };
    (status, Json(serde_json::json!({
        "status": overall,
        "components": { "postgres": postgres }
    })))
}
