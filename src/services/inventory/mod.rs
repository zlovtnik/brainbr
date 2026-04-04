use chrono::{DateTime, Utc};
use serde::Serialize;
use sqlx::{PgPool, Row};
use uuid::Uuid;

use crate::api::middleware::error::AppError;
use crate::api::middleware::tenant::set_rls_session;

#[derive(Debug)]
pub struct InventoryListFilters {
    pub page: i64,
    pub limit: i64,
    pub include_inactive: bool,
    pub query: Option<String>,
    pub sort_by: SortBy,
    pub sort_order: SortOrder,
}

#[derive(Debug)]
pub enum SortBy { UpdatedAt, SkuId }
#[derive(Debug)]
pub enum SortOrder { Asc, Desc }

impl InventoryListFilters {
    pub fn parse(
        page: i64, limit: i64, include_inactive: bool,
        query: Option<String>, sort_by: Option<String>, sort_order: Option<String>,
    ) -> Result<Self, AppError> {
        if page < 1 { return Err(AppError::BadRequest("page must be >= 1".into())); }
        if !(1..=100).contains(&limit) { return Err(AppError::BadRequest("limit must be 1-100".into())); }
        let sort_by = match sort_by.as_deref() {
            None | Some("updated_at") => SortBy::UpdatedAt,
            Some("sku_id") => SortBy::SkuId,
            Some(v) => return Err(AppError::BadRequest(format!("Invalid sort_by: {v}"))),
        };
        let sort_order = match sort_order.as_deref() {
            None | Some("desc") => SortOrder::Desc,
            Some("asc") => SortOrder::Asc,
            Some(v) => return Err(AppError::BadRequest(format!("Invalid sort_order: {v}"))),
        };
        Ok(Self { page, limit, include_inactive, query: query.filter(|s| !s.trim().is_empty()), sort_by, sort_order })
    }
}

#[derive(Serialize)]
pub struct InventorySkuResponse {
    pub sku_id: String,
    pub description: String,
    pub ncm_code: String,
    pub origin_state: String,
    pub destination_state: String,
    pub legacy_taxes: serde_json::Value,
    pub reform_taxes: serde_json::Value,
    pub is_active: bool,
    pub updated_at: DateTime<Utc>,
}

#[derive(Serialize)]
pub struct InventoryListResponse {
    pub items: Vec<InventorySkuResponse>,
    pub total_count: i64,
    pub page: i64,
    pub limit: i64,
    pub has_more: bool,
}

#[derive(Serialize)]
pub struct InventoryWriteResult {
    pub sku_id: String,
    pub status: String,
}

pub struct InventoryService;

impl InventoryService {
    pub async fn list(pool: &PgPool, company_id: Uuid, f: InventoryListFilters) -> Result<InventoryListResponse, AppError> {
        let offset = (f.page - 1) * f.limit;
        let search_pat = f.query.as_deref().map(to_search_pattern);
        let order_col = match f.sort_by { SortBy::UpdatedAt => "updated_at", SortBy::SkuId => "sku_id" };
        let order_dir = match f.sort_order { SortOrder::Asc => "ASC", SortOrder::Desc => "DESC" };

        let sql = format!(
            r#"SELECT sku_id, description, ncm_code, origin_state, destination_state,
                      legacy_taxes, reform_taxes, is_active, updated_at
               FROM inventory_transition
               WHERE company_id = $1
                 AND (is_active = TRUE OR $2 = TRUE)
                 AND ($3::text IS NULL OR sku_id ILIKE $3 ESCAPE '\' OR description ILIKE $3 ESCAPE '\' OR ncm_code ILIKE $3 ESCAPE '\')
               ORDER BY {order_col} {order_dir}
               LIMIT $4 OFFSET $5"#
        );

        let mut tx = pool.begin().await?;
        set_rls_session(&mut tx, company_id).await.map_err(AppError::Database)?;

        let rows = sqlx::query(&sql)
            .bind(company_id).bind(f.include_inactive).bind(&search_pat).bind(f.limit).bind(offset)
            .fetch_all(&mut *tx).await?;

        let total: i64 = sqlx::query_scalar(
            r#"SELECT COUNT(*) FROM inventory_transition
               WHERE company_id = $1 AND (is_active = TRUE OR $2 = TRUE)
                 AND ($3::text IS NULL OR sku_id ILIKE $3 ESCAPE '\' OR description ILIKE $3 ESCAPE '\' OR ncm_code ILIKE $3 ESCAPE '\')"#
        )
        .bind(company_id).bind(f.include_inactive).bind(&search_pat)
        .fetch_one(&mut *tx).await?;

        tx.commit().await?;

        let items = rows.iter().map(|r| InventorySkuResponse {
            sku_id: r.get("sku_id"), description: r.get("description"), ncm_code: r.get("ncm_code"),
            origin_state: r.get("origin_state"), destination_state: r.get("destination_state"),
            legacy_taxes: r.get("legacy_taxes"), reform_taxes: r.get("reform_taxes"),
            is_active: r.get("is_active"), updated_at: r.get("updated_at"),
        }).collect::<Vec<_>>();

        Ok(InventoryListResponse { has_more: f.page * f.limit < total, total_count: total, page: f.page, limit: f.limit, items })
    }

    pub async fn get(pool: &PgPool, company_id: Uuid, sku_id: &str, include_inactive: bool) -> Result<InventorySkuResponse, AppError> {
        let mut tx = pool.begin().await?;
        set_rls_session(&mut tx, company_id).await.map_err(AppError::Database)?;

        let r = sqlx::query(
            "SELECT sku_id, description, ncm_code, origin_state, destination_state, legacy_taxes, reform_taxes, is_active, updated_at
             FROM inventory_transition WHERE sku_id = $1 AND company_id = $2 AND (is_active = TRUE OR $3 = TRUE)"
        )
        .bind(sku_id).bind(company_id).bind(include_inactive)
        .fetch_optional(&mut *tx).await?
        .ok_or_else(|| AppError::NotFound(format!("SKU {sku_id} not found")))?;

        tx.commit().await?;

        Ok(InventorySkuResponse {
            sku_id: r.get("sku_id"), description: r.get("description"), ncm_code: r.get("ncm_code"),
            origin_state: r.get("origin_state"), destination_state: r.get("destination_state"),
            legacy_taxes: r.get("legacy_taxes"), reform_taxes: r.get("reform_taxes"),
            is_active: r.get("is_active"), updated_at: r.get("updated_at"),
        })
    }

    pub async fn upsert(pool: &PgPool, company_id: Uuid, body: serde_json::Value) -> Result<InventoryWriteResult, AppError> {
        let sku_id = body["sku_id"].as_str().ok_or_else(|| AppError::BadRequest("sku_id required".into()))?.to_string();
        let legacy_taxes = body.get("legacy_taxes").cloned().unwrap_or(serde_json::json!({}));

        let mut tx = pool.begin().await?;
        set_rls_session(&mut tx, company_id).await.map_err(AppError::Database)?;

        let row = sqlx::query(
            r#"INSERT INTO inventory_transition (sku_id, company_id, description, ncm_code, origin_state, destination_state, legacy_taxes, is_active)
               VALUES ($1, $2, $3, $4, $5, $6, $7::jsonb, TRUE)
               ON CONFLICT (sku_id, company_id) DO UPDATE SET
                   description = EXCLUDED.description, ncm_code = EXCLUDED.ncm_code,
                   origin_state = EXCLUDED.origin_state, destination_state = EXCLUDED.destination_state,
                   legacy_taxes = EXCLUDED.legacy_taxes, is_active = TRUE, updated_at = NOW()
               RETURNING (xmax = 0) AS created"#
        )
        .bind(&sku_id).bind(company_id)
        .bind(body["description"].as_str().unwrap_or(""))
        .bind(body["ncm_code"].as_str().unwrap_or(""))
        .bind(body["origin_state"].as_str().unwrap_or(""))
        .bind(body["destination_state"].as_str().unwrap_or(""))
        .bind(legacy_taxes)
        .fetch_one(&mut *tx).await?;

        tx.commit().await?;
        let created: bool = row.get("created");
        Ok(InventoryWriteResult { sku_id, status: if created { "created" } else { "updated" }.into() })
    }

    pub async fn update(pool: &PgPool, company_id: Uuid, sku_id: &str, body: serde_json::Value) -> Result<InventoryWriteResult, AppError> {
        let mut tx = pool.begin().await?;
        set_rls_session(&mut tx, company_id).await.map_err(AppError::Database)?;

        let rows = sqlx::query(
            r#"UPDATE inventory_transition SET
                   description = COALESCE($1, description),
                   ncm_code = COALESCE($2, ncm_code),
                   origin_state = COALESCE($3, origin_state),
                   destination_state = COALESCE($4, destination_state),
                   legacy_taxes = COALESCE($5::jsonb, legacy_taxes),
                   is_active = TRUE,
                   updated_at = NOW()
               WHERE sku_id = $6 AND company_id = $7"#
        )
        .bind(body["description"].as_str())
        .bind(body["ncm_code"].as_str())
        .bind(body["origin_state"].as_str())
        .bind(body["destination_state"].as_str())
        .bind(body.get("legacy_taxes").filter(|v| !v.is_null()).cloned())
        .bind(sku_id).bind(company_id)
        .execute(&mut *tx).await?.rows_affected();

        tx.commit().await?;
        if rows == 0 { return Err(AppError::NotFound(format!("SKU {sku_id} not found"))); }
        Ok(InventoryWriteResult { sku_id: sku_id.into(), status: "updated".into() })
    }

    pub async fn delete(pool: &PgPool, company_id: Uuid, sku_id: &str) -> Result<serde_json::Value, AppError> {
        let mut tx = pool.begin().await?;
        set_rls_session(&mut tx, company_id).await.map_err(AppError::Database)?;

        let rows = sqlx::query(
            "UPDATE inventory_transition SET is_active=FALSE, updated_at=NOW() WHERE sku_id=$1 AND company_id=$2 AND is_active=TRUE"
        )
        .bind(sku_id).bind(company_id)
        .execute(&mut *tx).await?.rows_affected();

        tx.commit().await?;
        if rows == 0 { return Err(AppError::NotFound(format!("SKU {sku_id} not found"))); }
        Ok(serde_json::json!({ "sku_id": sku_id, "status": "deleted" }))
    }
}

fn to_search_pattern(q: &str) -> String {
    let escaped = q.replace('\\', "\\\\").replace('%', "\\%").replace('_', "\\_");
    format!("%{escaped}%")
}
