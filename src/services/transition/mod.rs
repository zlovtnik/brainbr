pub mod math;
pub use math::{blended_burden, compute_risk_score};

use serde::Serialize;
use sqlx::{PgPool, Row};
use uuid::Uuid;

use crate::api::middleware::error::AppError;
use crate::api::middleware::tenant::set_rls_session;

#[derive(Serialize)]
pub struct TransitionCalendarResponse {
    pub years: Vec<TransitionYearResponse>,
}

#[derive(Serialize)]
pub struct TransitionYearResponse {
    pub year: i32,
    pub reform_weight: f64,
    pub legacy_weight: f64,
}

#[derive(Serialize)]
pub struct EffectiveRateResponse {
    pub sku_id: String,
    pub year: i32,
    pub blended_burden: BlendedBurdenResponse,
}

#[derive(Serialize)]
pub struct BlendedBurdenResponse {
    pub legacy_component: f64,
    pub reform_component: f64,
    pub total: f64,
    pub currency: String,
}

pub struct TransitionService;

impl TransitionService {
    pub async fn calendar(pool: &PgPool) -> Result<TransitionCalendarResponse, AppError> {
        let rows = sqlx::query(
            r#"SELECT year::int,
                      ibs_pct::float8 AS reform_weight,
                      (1.0 - ibs_pct::float8) AS legacy_weight
               FROM transition_calendar ORDER BY year"#,
        )
        .fetch_all(pool)
        .await?;

        Ok(TransitionCalendarResponse {
            years: rows.iter().map(|r| TransitionYearResponse {
                year: r.get::<i32, _>("year"),
                reform_weight: r.get::<f64, _>("reform_weight"),
                legacy_weight: r.get::<f64, _>("legacy_weight"),
            }).collect(),
        })
    }

    pub async fn effective_rate(
        pool: &PgPool,
        company_id: Uuid,
        sku_id: &str,
        year: i32,
    ) -> Result<EffectiveRateResponse, AppError> {
        if !(2026..=2033).contains(&year) {
            return Err(AppError::BadRequest("year must be between 2026 and 2033".into()));
        }

        let w = sqlx::query(
            r#"SELECT ibs_pct::float8 AS reform_weight,
                      (1.0 - ibs_pct::float8) AS legacy_weight
               FROM transition_calendar WHERE year = $1"#,
        )
        .bind(year as i16)
        .fetch_optional(pool)
        .await?
        .ok_or_else(|| AppError::BadRequest(format!("No transition weights for year {year}")))?;

        let rw: f64 = w.get("reform_weight");
        let lw: f64 = w.get("legacy_weight");

        let mut tx = pool.begin().await?;
        set_rls_session(&mut tx, company_id).await.map_err(AppError::Database)?;

        let record = sqlx::query(
            "SELECT legacy_taxes, reform_taxes FROM inventory_transition WHERE sku_id = $1 AND company_id = $2 AND is_active = TRUE",
        )
        .bind(sku_id)
        .bind(company_id)
        .fetch_optional(&mut *tx)
        .await?
        .ok_or_else(|| AppError::NotFound(format!("SKU {sku_id} not found")))?;

        tx.commit().await?;

        let legacy: serde_json::Value = record.try_get("legacy_taxes")
            .map_err(|e| AppError::Internal(anyhow::anyhow!("legacy_taxes: {e}")))?;
        let reform: serde_json::Value = record.try_get("reform_taxes")
            .map_err(|e| AppError::Internal(anyhow::anyhow!("reform_taxes: {e}")))?;
        let (lc, rc, total) = blended_burden(&legacy, &reform, lw, rw);

        Ok(EffectiveRateResponse {
            sku_id: sku_id.to_string(),
            year,
            blended_burden: BlendedBurdenResponse { legacy_component: lc, reform_component: rc, total, currency: "BRL".into() },
        })
    }
}
