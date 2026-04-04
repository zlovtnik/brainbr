use anyhow::{Context, Result};
use std::env;

#[derive(Clone)]
pub struct AppConfig {
    pub database_url: String,
    pub db_pool_max: u32,
    pub redis_url: String,
    pub models: ModelsConfig,
    pub queue: QueueConfig,
    pub worker: WorkerConfig,
    pub security: SecurityConfig,
    pub log_level: String,
    pub log_format: String,
}

impl std::fmt::Debug for AppConfig {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("AppConfig")
            .field("database_url", &redact_db_url(&self.database_url))
            .field("db_pool_max", &self.db_pool_max)
            .field("redis_url", &self.redis_url)
            .field("models", &self.models)
            .field("queue", &self.queue)
            .field("worker", &self.worker)
            .field("security", &self.security)
            .field("log_level", &self.log_level)
            .field("log_format", &self.log_format)
            .finish()
    }
}

#[derive(Clone)]
pub struct ModelsConfig {
    pub embedding: String,
    pub llm: String,
    pub openai_base_url: String,
    pub openai_api_key: String,
    pub provider_mode: String,
}

impl std::fmt::Debug for ModelsConfig {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("ModelsConfig")
            .field("embedding", &self.embedding)
            .field("llm", &self.llm)
            .field("openai_base_url", &self.openai_base_url)
            .field("openai_api_key", &"[REDACTED]")
            .field("provider_mode", &self.provider_mode)
            .finish()
    }
}

#[derive(Debug, Clone)]
pub struct QueueConfig {
    pub stream_ingestion: String,
    pub stream_audit: String,
    pub stream_reporting: String,
    pub stream_ingestion_dlq: String,
    pub stream_audit_dlq: String,
    pub group_ingestion: String,
    pub group_audit: String,
    pub retry_max_attempts: u32,
    pub retry_base_backoff_ms: u64,
}

#[derive(Debug, Clone)]
pub struct WorkerConfig {
    pub heartbeat_interval_ms: u64,
    pub ingestion_poll_interval_ms: u64,
    pub audit_poll_interval_ms: u64,
    pub transition_refresh_interval_ms: u64,
}

#[derive(Debug, Clone)]
pub struct SecurityConfig {
    pub jwt_issuer_uri: Option<String>,
    pub jwt_jwk_set_uri: Option<String>,
    pub jwt_tenant_claim: String,
    pub jwt_enabled: bool,
    pub bypass_rls_enabled: bool,
    pub dev_tenant_id: Option<String>,
}

impl AppConfig {
    pub fn from_env() -> Result<Self> {
        Ok(Self {
            database_url: required("DATABASE_URL")?,
            db_pool_max: optional("DB_POOL_MAX", "10").parse().context("DB_POOL_MAX must be a number")?,
            redis_url: optional("REDIS_URL", "redis://localhost:6379/0"),
            models: ModelsConfig {
                embedding: optional("EMBEDDING_MODEL", "text-embedding-3-small"),
                llm: optional("LLM_MODEL", "gpt-4o"),
                openai_base_url: optional("APP_PROVIDERS_OPENAI_BASE_URL", "https://api.openai.com/v1"),
                openai_api_key: optional("APP_PROVIDERS_OPENAI_API_KEY", ""),
                provider_mode: optional("MODEL_PROVIDER_MODE", "real"),
            },
            queue: QueueConfig {
                stream_ingestion: optional("APP_QUEUE_STREAM_INGESTION", "queue_ingestion"),
                stream_audit: optional("APP_QUEUE_STREAM_AUDIT", "queue_audit"),
                stream_reporting: optional("APP_QUEUE_STREAM_REPORTING", "queue_reporting"),
                stream_ingestion_dlq: optional("APP_QUEUE_STREAM_INGESTION_DLQ", "queue_ingestion_dlq"),
                stream_audit_dlq: optional("APP_QUEUE_STREAM_AUDIT_DLQ", "queue_audit_dlq"),
                group_ingestion: optional("APP_QUEUE_GROUP_INGESTION", "ingestion-workers"),
                group_audit: optional("APP_QUEUE_GROUP_AUDIT", "audit-workers"),
                retry_max_attempts: optional("APP_QUEUE_RETRY_MAX_ATTEMPTS", "3").parse().context("APP_QUEUE_RETRY_MAX_ATTEMPTS must be a number")?,
                retry_base_backoff_ms: optional("APP_QUEUE_RETRY_BASE_BACKOFF_MS", "1000").parse().context("APP_QUEUE_RETRY_BASE_BACKOFF_MS must be a number")?,
            },
            worker: WorkerConfig {
                heartbeat_interval_ms: optional("WORKER_HEARTBEAT_INTERVAL_MS", "60000").parse().context("WORKER_HEARTBEAT_INTERVAL_MS must be a number")?,
                ingestion_poll_interval_ms: optional("WORKER_INGESTION_POLL_INTERVAL_MS", "2000").parse().context("WORKER_INGESTION_POLL_INTERVAL_MS must be a number")?,
                audit_poll_interval_ms: optional("WORKER_AUDIT_POLL_INTERVAL_MS", "2000").parse().context("WORKER_AUDIT_POLL_INTERVAL_MS must be a number")?,
                transition_refresh_interval_ms: optional("WORKER_TRANSITION_REFRESH_INTERVAL_MS", "3600000").parse().context("WORKER_TRANSITION_REFRESH_INTERVAL_MS must be a number")?,
            },
            security: SecurityConfig {
                jwt_issuer_uri: env::var("APP_SECURITY_JWT_ISSUER_URI").ok().filter(|s| !s.is_empty()),
                jwt_jwk_set_uri: env::var("APP_SECURITY_JWT_JWK_SET_URI").ok().filter(|s| !s.is_empty()),
                jwt_tenant_claim: optional("APP_SECURITY_JWT_TENANT_CLAIM", "tenant_id"),
                jwt_enabled: optional("APP_SECURITY_JWT_ENABLED", "true").parse().context("APP_SECURITY_JWT_ENABLED must be true/false")?,
                bypass_rls_enabled: optional("APP_BYPASS_RLS_ENABLED", "false").parse().context("APP_BYPASS_RLS_ENABLED must be true/false")?,
                dev_tenant_id: env::var("APP_DEV_TENANT_ID").ok().filter(|s| !s.is_empty()),
            },
            log_level: optional("LOG_LEVEL", "INFO"),
            log_format: optional("LOG_FORMAT", "text"),
        })
    }
}

fn required(key: &str) -> Result<String> {
    env::var(key).with_context(|| format!("Missing required env var: {key}"))
}

fn optional(key: &str, default: &str) -> String {
    env::var(key).unwrap_or_else(|_| default.to_string())
}

fn redact_db_url(url: &str) -> String {
    if let Some(at) = url.rfind('@') {
        if let Some(scheme_end) = url.find("://") {
            let scheme = &url[..scheme_end + 3];
            let host_onwards = &url[at..];
            return format!("{scheme}<redacted>{host_onwards}");
        }
    }
    "<redacted>".to_string()
}
