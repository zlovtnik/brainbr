use regex::Regex;
use serde::{Deserialize, Serialize};
use uuid::Uuid;
use chrono::{DateTime, Utc, NaiveDate};
use sqlx::PgPool;

use crate::api::middleware::error::AppError;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct IngestionJob {
    pub job_id: String,
    pub company_id: Uuid,
    pub law_ref: String,
    pub law_type: String,
    pub source_url: Option<String>,
    pub raw_content: Option<String>,
    pub published_at: Option<NaiveDate>,
    pub effective_at: Option<NaiveDate>,
    pub tags: Vec<String>,
    pub request_id: Option<String>,
    pub attempt: i32,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AuditJob {
    pub job_id: String,
    pub company_id: Uuid,
    pub sku_id: String,
    pub request_id: Option<String>,
    pub attempt: i32,
    pub created_at: DateTime<Utc>,
}

pub struct IngestionService;

impl IngestionService {
    pub async fn enqueue(
        pool: &PgPool,
        company_id: Uuid,
        body: serde_json::Value,
        request_id: Option<&str>,
    ) -> Result<serde_json::Value, AppError> {
        let job_id = Uuid::new_v4();
        let law_ref = body["law_ref"].as_str().ok_or_else(|| AppError::BadRequest("law_ref required".into()))?.to_string();
        let law_type = body["law_type"].as_str().ok_or_else(|| AppError::BadRequest("law_type required".into()))?.to_string();
        let source_url = body["source_url"].as_str().map(String::from);
        let raw_content = body["raw_content"].as_str().map(String::from);

        // Persist job record so workers can pick it up
        sqlx::query(
            r#"INSERT INTO fiscal_audit_log (company_id, sku_id, event_type, actor, request_id, event_payload)
               VALUES ($1, 'ingestion', 'INGESTION_QUEUED', 'api', $2, $3::jsonb)"#
        )
        .bind(company_id)
        .bind(request_id)
        .bind(serde_json::json!({
            "job_id": job_id,
            "law_ref": law_ref,
            "law_type": law_type,
            "source_url": source_url,
        }))
        .execute(pool)
        .await?;

        Ok(serde_json::json!({
            "job_id": job_id,
            "status": "queued",
            "law_ref": law_ref,
            "law_type": law_type,
            "company_id": company_id,
            "request_id": request_id,
        }))
    }
}

/// Ported from ChunkingService.kt
pub fn chunk_text(content: &str, max_chunk_chars: usize, overlap_chars: usize) -> Result<Vec<String>, AppError> {
    if max_chunk_chars == 0 {
        return Err(AppError::BadRequest("max_chunk_chars must be > 0".into()));
    }
    if overlap_chars >= max_chunk_chars {
        return Err(AppError::BadRequest("overlap_chars must be < max_chunk_chars".into()));
    }

    let normalized = content.replace("\r\n", "\n").trim().to_string();
    if normalized.is_empty() { return Ok(vec![]); }

    let article_re = Regex::new(r"(?i)\bArt\.?\s+\d+[\w\-]*").unwrap();
    let blocks = split_on_articles(&normalized, &article_re);
    let mut chunks = Vec::new();
    for block in blocks {
        chunks.extend(split_with_overlap(&block, max_chunk_chars, overlap_chars));
    }
    Ok(chunks)
}

fn split_on_articles(content: &str, re: &Regex) -> Vec<String> {
    let matches: Vec<_> = re.find_iter(content).collect();
    if matches.is_empty() { return vec![content.to_string()]; }

    let mut parts = Vec::new();
    let first_start = matches[0].start();
    if first_start > 0 {
        let pre = content[..first_start].trim();
        if !pre.is_empty() { parts.push(pre.to_string()); }
    }
    for (i, m) in matches.iter().enumerate() {
        let start = m.start();
        let end = matches.get(i + 1).map(|n| n.start()).unwrap_or(content.len());
        let piece = content[start..end].trim();
        if !piece.is_empty() { parts.push(piece.to_string()); }
    }
    parts
}

fn split_with_overlap(text: &str, max: usize, overlap: usize) -> Vec<String> {
    let mut chunks = Vec::new();
    let chars: Vec<char> = text.chars().collect();
    let len = chars.len();
    let mut start = 0usize;

    while start < len {
        let end = (start + max).min(len);
        let piece: String = chars[start..end].iter().collect();
        let piece = piece.trim().to_string();
        if !piece.is_empty() { chunks.push(piece); }
        if end >= len { break; }
        start = if end > overlap { end - overlap } else { start + 1 };
    }
    chunks
}

/// Ported from VectorUtils.kt
pub fn to_vector_literal(values: &[f64]) -> String {
    let inner = values.iter().map(|v| format!("{v:.10}")).collect::<Vec<_>>().join(",");
    format!("[{inner}]")
}

pub fn hash_content(input: &str) -> String {
    use sha2::{Sha256, Digest};
    use unicode_normalization::UnicodeNormalization;
    let normalized: String = input.trim().nfkc().collect();
    let digest = Sha256::digest(normalized.as_bytes());
    hex::encode(digest)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn chunk_empty() {
        assert!(chunk_text("", 1200, 120).unwrap().is_empty());
    }

    #[test]
    fn chunk_short_text() {
        let chunks = chunk_text("Hello world", 1200, 120).unwrap();
        assert_eq!(chunks.len(), 1);
        assert_eq!(chunks[0], "Hello world");
    }

    #[test]
    fn chunk_invalid_params() {
        assert!(chunk_text("x", 0, 0).is_err());
        assert!(chunk_text("x", 10, 10).is_err());
    }

    #[test]
    fn hash_is_deterministic() {
        assert_eq!(hash_content("test"), hash_content("test"));
    }
}
