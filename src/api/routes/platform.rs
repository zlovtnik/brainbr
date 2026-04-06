use axum::{extract::State, response::IntoResponse, Json};
use std::sync::Arc;

use crate::config::AppConfig;

pub async fn info(State(cfg): State<Arc<AppConfig>>) -> impl IntoResponse {
    Json(serde_json::json!({
        "service": "fiscalbrain-br",
        "embeddingModel": cfg.models.embedding,
        "llmModel": cfg.models.llm,
    }))
}
