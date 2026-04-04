use axum::extract::Request;
use once_cell::sync::Lazy;
use regex::Regex;
use uuid::Uuid;

pub mod auth;
pub mod tenant;
pub mod error;

pub use error::AppError;

pub fn generate_request_id(raw: Option<&str>) -> String {
    static ALLOWED: Lazy<Regex> =
        Lazy::new(|| Regex::new(r"^[A-Za-z0-9._:\-]{1,64}$").unwrap());

    raw.and_then(|s| {
        let s = s.trim();
        if ALLOWED.is_match(s) { Some(s.to_string()) } else { None }
    })
    .unwrap_or_else(|| Uuid::new_v4().to_string())
}
