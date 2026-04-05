use serde::{Deserialize, Serialize};
use uuid::Uuid;
use chrono::{DateTime, Utc};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Company {
    pub id: Uuid,
    pub external_tenant_id: String,
    pub name: String,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct InventoryRecord {
    pub sku_id: String,
    pub company_id: Uuid,
    pub description: String,
    pub ncm_code: String,
    pub origin_state: String,
    pub destination_state: String,
    pub legacy_taxes: serde_json::Value,
    pub reform_taxes: serde_json::Value,
    pub is_active: bool,
    pub transition_risk_score: Option<i16>,
    pub audit_confidence: Option<f64>,
    pub llm_model_used: Option<String>,
    pub vector_id: Option<Uuid>,
    pub last_llm_audit: Option<DateTime<Utc>>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TransitionCalendar {
    pub year: i16,
    pub ibs_pct: f64,
    pub cbs_pct: f64,
    pub icms_factor: f64,
    pub iss_factor: f64,
    pub pis_active: bool,
    pub cofins_active: bool,
    pub notes: Option<String>,
    pub law_ref: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct KnowledgeChunk {
    pub id: Uuid,
    pub knowledge_id: Uuid,
    pub company_id: Uuid,
    pub chunk_index: i32,
    pub content: String,
    pub metadata: serde_json::Value,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AuditExplainabilityRun {
    pub id: Uuid,
    pub company_id: Uuid,
    pub sku_id: String,
    pub job_id: String,
    pub request_id: Option<String>,
    pub artifact_version: String,
    pub schema_version: String,
    pub llm_model_used: String,
    pub vector_id: Uuid,
    pub audit_confidence: f64,
    pub source_snapshot: serde_json::Value,
    pub replay_context: serde_json::Value,
    pub rag_output: serde_json::Value,
    pub artifact_digest: String,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SplitPaymentEvent {
    pub id: Uuid,
    pub company_id: Uuid,
    pub sku_id: String,
    pub event_type: String,
    pub amount: i64,
    pub currency: String,
    pub idempotency_key: String,
    pub event_timestamp: DateTime<Utc>,
    pub integration_status: String,
    pub integration_metadata: serde_json::Value,
    pub event_payload: serde_json::Value,
    pub request_id: Option<String>,
    pub created_at: DateTime<Utc>,
}
