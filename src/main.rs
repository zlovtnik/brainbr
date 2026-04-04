use fiscalbrain_br::{api, config::AppConfig, db};
use std::net::SocketAddr;
use std::sync::Arc;
use tracing_subscriber::EnvFilter;

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    dotenvy::dotenv().ok();

    let cfg = AppConfig::from_env()?;

    let filter = EnvFilter::try_from_default_env()
        .unwrap_or_else(|_| EnvFilter::new(&cfg.log_level));
    if cfg.log_format == "json" {
        tracing_subscriber::fmt().json().with_env_filter(filter).init();
    } else {
        tracing_subscriber::fmt().with_env_filter(filter).init();
    }

    tracing::info!("Starting fiscalbrain-br API");

    let pool = Arc::new(db::create_pool(&cfg.database_url, cfg.db_pool_max).await?);
    db::run_migrations(&pool).await?;

    let cfg = Arc::new(cfg);
    let router = api::build_router(pool, cfg);

    let addr = SocketAddr::from(([0, 0, 0, 0], 8080));
    tracing::info!("Listening on {addr}");
    let listener = tokio::net::TcpListener::bind(addr).await?;
    axum::serve(listener, router).await?;

    Ok(())
}
