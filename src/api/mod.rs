pub mod routes;
pub mod middleware;

use axum::{
    middleware as axum_middleware,
    routing::{get, post, put, delete},
    Router,
};
use sqlx::PgPool;
use std::sync::Arc;

use crate::config::AppConfig;
use middleware::auth::{auth_middleware, JwksState};
use middleware::error::request_id_middleware;
use middleware::tenant::tenant_middleware;
use routes::health::HealthRegistry;

#[derive(Clone)]
pub struct AppState {
    pub pool: Arc<PgPool>,
    pub config: Arc<AppConfig>,
}

pub fn build_router(pool: Arc<PgPool>, config: Arc<AppConfig>) -> Router {
    let jwks = Arc::new(JwksState::new(&config.security));
    let registry = Arc::new(HealthRegistry::new(pool.clone()));
    let state = AppState { pool: pool.clone(), config: config.clone() };

    let public = Router::new()
        .route("/actuator/health", get(routes::health::health))
        .route("/actuator/health/liveness", get(routes::health::liveness))
        .route("/actuator/health/readiness", get(routes::health::readiness))
        .with_state(registry)
        .route("/api/v1/platform/info", get(routes::platform::info))
        .with_state(config.clone());

    let protected = Router::new()
        .route("/api/v1/transition/calendar", get(routes::transition::calendar))
        .route("/api/v1/transition/sku/:sku_id/effective-rate", get(routes::transition::effective_rate))
        .route("/api/v1/transition/sku/:sku_id/forecast", get(routes::transition::forecast))
        .route("/api/v1/inventory/sku", get(routes::inventory::list))
        .route("/api/v1/inventory/sku", post(routes::inventory::upsert))
        .route("/api/v1/inventory/sku/:sku_id", get(routes::inventory::get))
        .route("/api/v1/inventory/sku/:sku_id", put(routes::inventory::update))
        .route("/api/v1/inventory/sku/:sku_id", delete(routes::inventory::delete))
        .route("/api/v1/audit/explain/:sku_id", get(routes::audit::explain))
        .route("/api/v1/audit/explain/:sku_id/artifact/latest", get(routes::audit::explain_latest_artifact))
        .route("/api/v1/audit/explain/artifact/runs/:run_id", get(routes::audit::explain_artifact_by_run))
        .route("/api/v1/audit/query", post(routes::audit::query))
        .route("/api/v1/inventory/sku/:sku_id/re-audit", post(routes::audit::re_audit))
        .route("/api/v1/split-payment/events", post(routes::split_payment::create))
        .route("/api/v1/split-payment/events", get(routes::split_payment::list))
        .route("/api/v1/ingestion/jobs", post(routes::ingestion::create_job))
        .with_state(state)
        .layer(axum_middleware::from_fn_with_state(pool.clone(), tenant_middleware))
        .layer(axum_middleware::from_fn_with_state(jwks, auth_middleware))
        .layer(axum_middleware::from_fn(request_id_middleware));

    public.merge(protected)
}
