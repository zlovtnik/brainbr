use axum::{
    http::StatusCode,
    response::{IntoResponse, Response},
    Json,
};
use serde::Serialize;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum AppError {
    #[error("Not found: {0}")]
    NotFound(String),
    #[error("Bad request: {0}")]
    BadRequest(String),
    #[error("Forbidden: {0}")]
    Forbidden(String),
    #[error("Unauthorized")]
    Unauthorized,
    #[error("Internal error: {0}")]
    Internal(#[from] anyhow::Error),
    #[error("Database error: {0}")]
    Database(#[from] sqlx::Error),
}

#[derive(Serialize)]
struct ErrorBody {
    error_code: String,
    message: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    request_id: Option<String>,
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let (status, code, msg) = match &self {
            AppError::NotFound(m) => (StatusCode::NOT_FOUND, "NOT_FOUND", m.clone()),
            AppError::BadRequest(m) => (StatusCode::BAD_REQUEST, "BAD_REQUEST", m.clone()),
            AppError::Forbidden(m) => (StatusCode::FORBIDDEN, "FORBIDDEN", m.clone()),
            AppError::Unauthorized => (StatusCode::UNAUTHORIZED, "UNAUTHORIZED", "Missing or invalid credentials".into()),
            AppError::Internal(e) => {
                tracing::error!("Internal error: {e:#}");
                (StatusCode::INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Unexpected error".into())
            }
            AppError::Database(e) => {
                tracing::error!("Database error: {e}");
                (StatusCode::INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Unexpected error".into())
            }
        };
        (status, Json(ErrorBody { error_code: code.into(), message: msg, request_id: None })).into_response()
    }
}
