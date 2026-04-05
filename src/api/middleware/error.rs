use axum::{
    extract::Request,
    http::StatusCode,
    middleware::Next,
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

/// Middleware that generates/validates a request ID, inserts it as
/// `Extension<Option<String>>` for handlers, and echoes it in the response
/// header so clients can correlate errors.
pub async fn request_id_middleware(mut req: Request, next: Next) -> Response {
    let raw = req.headers()
        .get("x-request-id")
        .and_then(|v| v.to_str().ok());
    let request_id = crate::api::middleware::generate_request_id(raw);
    req.extensions_mut().insert(Some(request_id.clone()));
    let mut response = next.run(req).await;
    if let Ok(v) = request_id.parse() {
        response.headers_mut().insert("x-request-id", v);
    }
    response
}
