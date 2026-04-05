use regex::Regex;
use serde::{Deserialize, Serialize};
use uuid::Uuid;
use chrono::{DateTime, Utc, NaiveDate};
use sqlx::PgPool;

use crate::api::middleware::error::AppError;
use crate::config::ModelsConfig;
use crate::services::rag::RagService;

/// Valid Brazilian state abbreviations (UF).
const VALID_UF: &[&str] = &[
    "AC","AL","AP","AM","BA","CE","DF","ES","GO","MA",
    "MT","MS","MG","PA","PB","PR","PE","PI","RJ","RN",
    "RS","RO","RR","SC","SP","SE","TO",
];

/// Validated metadata extracted from an ingestion request body.
#[derive(Debug, Clone, Default)]
pub struct IngestionMetadata {
    /// Optional Brazilian state (UF) this legislation applies to.
    pub state: Option<String>,
    /// Optional list of NCM chapter/heading prefixes (e.g. `["22", "2203"]`).
    pub ncm_scope: Vec<String>,
    /// Free-form tags.
    pub tags: Vec<String>,
}

impl IngestionMetadata {
    /// Parse and validate `state`, `ncm_scope`, and `tags` from a JSON body.
    pub fn from_body(body: &serde_json::Value) -> Result<Self, AppError> {
        let state = match body["state"].as_str() {
            None | Some("") => None,
            Some(s) => {
                let upper = s.to_uppercase();
                if !VALID_UF.contains(&upper.as_str()) {
                    return Err(AppError::BadRequest(format!(
                        "state '{}' is not a valid Brazilian UF", s
                    )));
                }
                Some(upper)
            }
        };

        let ncm_scope = match body["ncm_scope"].as_array() {
            None => vec![],
            Some(arr) => {
                let mut out = Vec::with_capacity(arr.len());
                for v in arr {
                    let prefix = v.as_str().ok_or_else(|| {
                        AppError::BadRequest("ncm_scope entries must be strings".into())
                    })?;
                    if prefix.is_empty() || !prefix.chars().all(|c| c.is_ascii_digit()) {
                        return Err(AppError::BadRequest(format!(
                            "ncm_scope entry '{}' must be a non-empty numeric string", prefix
                        )));
                    }
                    if prefix.len() > 8 {
                        return Err(AppError::BadRequest(format!(
                            "ncm_scope entry '{}' exceeds 8 digits", prefix
                        )));
                    }
                    out.push(prefix.to_string());
                }
                out
            }
        };

        let tags = body["tags"]
            .as_array()
            .map(|a| a.iter().filter_map(|v| v.as_str().map(String::from)).collect())
            .unwrap_or_default();

        Ok(Self { state, ncm_scope, tags })
    }

    pub fn to_json(&self) -> serde_json::Value {
        serde_json::json!({
            "tags": self.tags,
            "state": self.state,
            "ncm_scope": self.ncm_scope,
        })
    }
}

/// Signals that a job should be routed to DLQ immediately without retrying.
#[derive(Debug)]
pub struct NonRetryable(pub String);

impl std::fmt::Display for NonRetryable {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "NonRetryable: {}", self.0)
    }
}
impl std::error::Error for NonRetryable {}

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
    /// Validated Brazilian state (UF), if legislation is state-scoped.
    pub state: Option<String>,
    /// Validated NCM chapter/heading prefixes this legislation covers.
    pub ncm_scope: Vec<String>,
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

pub struct ReingestionService;

impl ReingestionService {
    /// Finds shared (`company_id IS NULL`) knowledge rows not updated within `staleness_ms`
    /// and enqueues an `IngestionJob` for each. Uses an advisory lock so only one worker runs
    /// the scan at a time. Returns the number of jobs enqueued, or `None` if lock was held.
    pub async fn enqueue_stale(
        pool: &PgPool,
        queue: &mut crate::queue::RedisQueueClient,
        staleness_ms: u64,
        ingestion_stream: &str,
    ) -> anyhow::Result<Option<usize>> {
        const LOCK_KEY: i64 = 7766554433_i64;
        let locked: bool = sqlx::query_scalar("SELECT pg_try_advisory_lock($1)")
            .bind(LOCK_KEY)
            .fetch_one(pool)
            .await?;
        if !locked {
            return Ok(None);
        }

        let result = Self::do_enqueue(pool, queue, staleness_ms, ingestion_stream).await;

        let _: bool = sqlx::query_scalar("SELECT pg_advisory_unlock($1)")
            .bind(LOCK_KEY)
            .fetch_one(pool)
            .await
            .unwrap_or(false);

        result.map(Some)
    }

    async fn do_enqueue(
        pool: &PgPool,
        queue: &mut crate::queue::RedisQueueClient,
        staleness_ms: u64,
        ingestion_stream: &str,
    ) -> anyhow::Result<usize> {
        let staleness_interval = format!("{} milliseconds", staleness_ms);
        let rows = sqlx::query(
            r#"SELECT id, law_ref, law_type, content, source_url, published_at, effective_at, metadata
               FROM fiscal_knowledge_base
               WHERE company_id IS NULL
                 AND is_superseded = FALSE
                 AND updated_at < NOW() - $1::interval
               ORDER BY updated_at ASC
               LIMIT 100"#,
        )
        .bind(&staleness_interval)
        .fetch_all(pool)
        .await?;

        let count = rows.len();
        // Use a fixed sentinel UUID for shared (global) knowledge jobs
        let global_company_id = Uuid::nil();

        for row in rows {
            use sqlx::Row;
            let law_ref: String = row.get("law_ref");
            let law_type: String = row.get("law_type");
            let content: String = row.get("content");
            let source_url: Option<String> = row.get("source_url");
            let published_at: Option<chrono::NaiveDate> = row.get("published_at");
            let effective_at: Option<chrono::NaiveDate> = row.get("effective_at");
            let metadata: serde_json::Value = row.get("metadata");
            let tags: Vec<String> = metadata["tags"]
                .as_array()
                .map(|a| a.iter().filter_map(|v| v.as_str().map(String::from)).collect())
                .unwrap_or_default();
            let state: Option<String> = metadata["state"].as_str().map(String::from);
            let ncm_scope: Vec<String> = metadata["ncm_scope"]
                .as_array()
                .map(|a| a.iter().filter_map(|v| v.as_str().map(String::from)).collect())
                .unwrap_or_default();

            let job = IngestionJob {
                job_id: Uuid::new_v4().to_string(),
                company_id: global_company_id,
                law_ref,
                law_type,
                source_url,
                raw_content: Some(content),
                published_at,
                effective_at,
                tags,
                state,
                ncm_scope,
                request_id: Some("reingest-scheduler".into()),
                attempt: 0,
                created_at: Utc::now(),
            };
            queue.enqueue(ingestion_stream, &job).await?;
        }
        Ok(count)
    }
}

pub struct IngestionService;

impl IngestionService {
    pub async fn process_job(pool: &PgPool, cfg: &ModelsConfig, job: IngestionJob) -> anyhow::Result<()> {
        let raw = job.raw_content.as_deref().unwrap_or("");
        let chunks = chunk_text(raw, 1200, 120).map_err(|e| anyhow::anyhow!("{e:?}"))?;
        let content_hash = hash_content(raw);

        let mut tx = pool.begin().await?;
        crate::api::middleware::tenant::set_rls_session(&mut tx, job.company_id)
            .await
            .map_err(|e| anyhow::anyhow!("{e:?}"))?;

        let kb_id: uuid::Uuid = sqlx::query_scalar(
            r#"INSERT INTO fiscal_knowledge_base
                   (company_id, law_ref, law_type, content, source_url, published_at, effective_at,
                    metadata, content_hash, content_version)
               VALUES ($1,$2,$3,$4,$5,$6,$7,$8::jsonb,$9,1)
               ON CONFLICT (law_ref, company_id) WHERE company_id IS NOT NULL
               DO UPDATE SET
                   content = EXCLUDED.content,
                   content_hash = EXCLUDED.content_hash,
                   content_version = fiscal_knowledge_base.content_version + 1,
                   updated_at = NOW()
               RETURNING id"#,
        )
        .bind(job.company_id)
        .bind(&job.law_ref)
        .bind(&job.law_type)
        .bind(raw)
        .bind(&job.source_url)
        .bind(job.published_at)
        .bind(job.effective_at)
        .bind(serde_json::json!({
            "tags": job.tags,
            "state": job.state,
            "ncm_scope": job.ncm_scope,
        }))
        .bind(&content_hash)
        .fetch_one(&mut *tx)
        .await?;

        // Embed all chunks in one batch call
        let chunk_refs: Vec<&str> = chunks.iter().map(|s| s.as_str()).collect();
        let embeddings = RagService::embed_batch(cfg, &chunk_refs).await
            .map_err(|e| anyhow::anyhow!("embedding batch failed: {e}"))?;

        // Remove stale chunks from previous versions that are beyond the new count
        sqlx::query("DELETE FROM fiscal_knowledge_chunk WHERE knowledge_id = $1 AND chunk_index >= $2")
            .bind(kb_id)
            .bind(chunks.len() as i32)
            .execute(&mut *tx)
            .await?;

        for (i, (chunk, embedding)) in chunks.iter().zip(embeddings.iter()).enumerate() {
            let vec_literal = to_vector_literal_f32(embedding);
            sqlx::query(
                r#"INSERT INTO fiscal_knowledge_chunk (knowledge_id, company_id, chunk_index, content, embedding, metadata)
                   VALUES ($1,$2,$3,$4,$5::vector,$6::jsonb)
                   ON CONFLICT (knowledge_id, chunk_index) DO UPDATE
                     SET content = EXCLUDED.content, embedding = EXCLUDED.embedding"#,
            )
            .bind(kb_id)
            .bind(job.company_id)
            .bind(i as i32)
            .bind(chunk)
            .bind(vec_literal)
            .bind(serde_json::json!({ "law_ref": job.law_ref, "chunk_index": i }))
            .execute(&mut *tx)
            .await?;
        }

        sqlx::query(
            r#"INSERT INTO fiscal_audit_log (company_id, sku_id, event_type, actor, request_id, event_payload)
               VALUES ($1,'ingestion','INGESTION_COMPLETE','worker',$2,$3::jsonb)"#,
        )
        .bind(job.company_id)
        .bind(&job.request_id)
        .bind(serde_json::json!({
            "job_id": job.job_id,
            "law_ref": job.law_ref,
            "kb_id": kb_id,
            "chunk_count": chunks.len(),
        }))
        .execute(&mut *tx)
        .await?;

        tx.commit().await?;
        Ok(())
    }

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
        let meta = IngestionMetadata::from_body(&body)?;

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
            "state": meta.state,
            "ncm_scope": meta.ncm_scope,
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

pub fn to_vector_literal_f32(values: &[f32]) -> String {
    let inner = values.iter().map(|v| format!("{v:.8}")).collect::<Vec<_>>().join(",");
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

    // ── IngestionMetadata validation ──────────────────────────────────────────

    #[test]
    fn metadata_valid_state_uppercased() {
        let body = serde_json::json!({ "state": "sp" });
        let m = IngestionMetadata::from_body(&body).unwrap();
        assert_eq!(m.state.as_deref(), Some("SP"));
    }

    #[test]
    fn metadata_invalid_state_rejected() {
        let body = serde_json::json!({ "state": "XX" });
        assert!(IngestionMetadata::from_body(&body).is_err());
    }

    #[test]
    fn metadata_absent_state_is_none() {
        let body = serde_json::json!({});
        let m = IngestionMetadata::from_body(&body).unwrap();
        assert!(m.state.is_none());
    }

    #[test]
    fn metadata_valid_ncm_scope() {
        let body = serde_json::json!({ "ncm_scope": ["22", "2203", "22030000"] });
        let m = IngestionMetadata::from_body(&body).unwrap();
        assert_eq!(m.ncm_scope, vec!["22", "2203", "22030000"]);
    }

    #[test]
    fn metadata_ncm_scope_non_numeric_rejected() {
        let body = serde_json::json!({ "ncm_scope": ["22AB"] });
        assert!(IngestionMetadata::from_body(&body).is_err());
    }

    #[test]
    fn metadata_ncm_scope_too_long_rejected() {
        let body = serde_json::json!({ "ncm_scope": ["220300001"] });
        assert!(IngestionMetadata::from_body(&body).is_err());
    }

    #[test]
    fn metadata_ncm_scope_empty_string_rejected() {
        let body = serde_json::json!({ "ncm_scope": [""] });
        assert!(IngestionMetadata::from_body(&body).is_err());
    }

    #[test]
    fn metadata_to_json_roundtrip() {
        let body = serde_json::json!({
            "state": "RJ",
            "ncm_scope": ["22"],
            "tags": ["beverages"]
        });
        let m = IngestionMetadata::from_body(&body).unwrap();
        let j = m.to_json();
        assert_eq!(j["state"], "RJ");
        assert_eq!(j["ncm_scope"][0], "22");
        assert_eq!(j["tags"][0], "beverages");
    }
}
