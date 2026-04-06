use chrono::{DateTime, Utc};
use serde::Serialize;
use sqlx::{PgPool, Row};
use uuid::Uuid;

use crate::api::middleware::error::AppError;
use crate::api::middleware::tenant::set_rls_session;

#[derive(Serialize)]
pub struct SplitPaymentCreateResponse {
    pub event_id: String,
    pub status: String,
    pub integration_status: String,
    pub created_at: DateTime<Utc>,
}

#[derive(Serialize)]
pub struct SplitPaymentEventResponse {
    pub event_id: String,
    pub sku_id: String,
    pub event_type: String,
    pub amount: i64,
    pub currency: String,
    pub idempotency_key: String,
    pub timestamp: DateTime<Utc>,
    pub integration_status: String,
    pub integration_metadata: serde_json::Value,
    pub event_payload: serde_json::Value,
    pub created_at: DateTime<Utc>,
}

#[derive(Serialize)]
pub struct SplitPaymentListResponse {
    pub items: Vec<SplitPaymentEventResponse>,
    pub total_count: i64,
    pub page: i64,
    pub limit: i64,
    pub has_more: bool,
}

pub struct SplitPaymentService;

impl SplitPaymentService {
    pub async fn create(
        pool: &PgPool,
        company_id: Uuid,
        body: serde_json::Value,
        request_id: Option<&str>,
    ) -> Result<SplitPaymentCreateResponse, AppError> {
        let sku_id = body["sku_id"].as_str().ok_or_else(|| AppError::BadRequest("sku_id required".into()))?;
        let event_type = body["event_type"].as_str().ok_or_else(|| AppError::BadRequest("event_type required".into()))?;
        let amount = body["amount"].as_i64().ok_or_else(|| AppError::BadRequest("amount required".into()))?;
        let currency = body["currency"].as_str().ok_or_else(|| AppError::BadRequest("currency required".into()))?.to_uppercase();
        let idempotency_key = body["idempotency_key"].as_str().ok_or_else(|| AppError::BadRequest("idempotency_key required".into()))?;
        let timestamp: DateTime<Utc> = body["timestamp"].as_str()
            .and_then(|s| s.parse().ok())
            .unwrap_or_else(Utc::now);
        let integration_metadata = body.get("integration_metadata").cloned().unwrap_or(serde_json::json!({}));
        let event_payload = body.get("event_payload").cloned().unwrap_or(serde_json::json!({}));

        let mut tx = pool.begin().await?;
        set_rls_session(&mut tx, company_id).await.map_err(AppError::Database)?;

        let inserted = sqlx::query(
            r#"INSERT INTO split_payment_events
                (company_id, sku_id, event_type, amount, currency, idempotency_key, event_timestamp, integration_status, integration_metadata, event_payload, request_id)
               VALUES ($1, $2, $3, $4, $5, $6, $7, 'queued', $8::jsonb, $9::jsonb, $10)
               ON CONFLICT (company_id, idempotency_key) DO NOTHING
               RETURNING id, integration_status, created_at"#
        )
        .bind(company_id).bind(sku_id).bind(event_type).bind(amount).bind(&currency)
        .bind(idempotency_key).bind(timestamp).bind(&integration_metadata).bind(&event_payload).bind(request_id)
        .fetch_optional(&mut *tx).await?;

        let (event_id, integration_status, created_at) = match inserted {
            Some(r) => (r.get::<Uuid, _>("id").to_string(), r.get::<String, _>("integration_status"), r.get::<DateTime<Utc>, _>("created_at")),
            None => {
                let existing = sqlx::query(
                    "SELECT id, integration_status, created_at FROM split_payment_events WHERE company_id=$1 AND idempotency_key=$2"
                )
                .bind(company_id).bind(idempotency_key)
                .fetch_one(&mut *tx).await?;
                (existing.get::<Uuid, _>("id").to_string(), existing.get("integration_status"), existing.get("created_at"))
            }
        };

        tx.commit().await?;

        Ok(SplitPaymentCreateResponse { event_id, status: "ok".into(), integration_status, created_at })
    }

    pub async fn list(
        pool: &PgPool,
        company_id: Uuid,
        page: i64,
        limit: i64,
        sku_id: Option<String>,
        event_type: Option<String>,
    ) -> Result<SplitPaymentListResponse, AppError> {
        if page < 1 { return Err(AppError::BadRequest("page must be >= 1".into())); }
        if !(1..=100).contains(&limit) { return Err(AppError::BadRequest("limit must be 1-100".into())); }

        let offset = (page - 1) * limit;

        let mut tx = pool.begin().await?;
        set_rls_session(&mut tx, company_id).await.map_err(AppError::Database)?;

        let rows = sqlx::query(
            r#"SELECT e.id, e.sku_id, e.event_type, e.amount, e.currency, e.idempotency_key,
                      e.event_timestamp, e.integration_status, e.integration_metadata, e.event_payload, e.created_at
               FROM split_payment_events e
               WHERE e.company_id = $1
                 AND ($2::text IS NULL OR e.sku_id = $2)
                 AND ($3::text IS NULL OR e.event_type = $3)
               ORDER BY e.created_at DESC LIMIT $4 OFFSET $5"#
        )
        .bind(company_id).bind(&sku_id).bind(&event_type).bind(limit).bind(offset)
        .fetch_all(&mut *tx).await?;

        let total: i64 = sqlx::query_scalar(
            "SELECT COUNT(*) FROM split_payment_events WHERE company_id=$1 AND ($2::text IS NULL OR sku_id=$2) AND ($3::text IS NULL OR event_type=$3)"
        )
        .bind(company_id).bind(&sku_id).bind(&event_type)
        .fetch_one(&mut *tx).await?;

        tx.commit().await?;

        let items = rows.iter().map(|r| SplitPaymentEventResponse {
            event_id: r.get::<Uuid, _>("id").to_string(),
            sku_id: r.get("sku_id"), event_type: r.get("event_type"),
            amount: r.get("amount"), currency: r.get("currency"),
            idempotency_key: r.get("idempotency_key"),
            timestamp: r.get("event_timestamp"),
            integration_status: r.get("integration_status"),
            integration_metadata: r.get("integration_metadata"),
            event_payload: r.get("event_payload"),
            created_at: r.get("created_at"),
        }).collect();

        Ok(SplitPaymentListResponse { items, total_count: total, page, limit, has_more: page * limit < total })
    }
}
