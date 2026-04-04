use fiscalbrain_br::{
    config::AppConfig,
    db,
    queue::RedisQueueClient,
    services::pipeline::{AuditJob, IngestionJob},
};
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::broadcast;
use tracing_subscriber::EnvFilter;
use uuid::Uuid;

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    dotenvy::dotenv().ok();

    let cfg = AppConfig::from_env()?;

    let filter = EnvFilter::try_from_default_env()
        .unwrap_or_else(|_| EnvFilter::new(&cfg.log_level));
    tracing_subscriber::fmt().with_env_filter(filter).init();

    tracing::info!("Starting fiscalbrain-br worker");

    let pool = Arc::new(db::create_pool(&cfg.database_url, cfg.db_pool_max).await?);
    let queue = RedisQueueClient::new(&cfg.redis_url, &cfg.queue).await?;

    let consumer_name = format!("worker-{}", Uuid::new_v4());
    let audit_poll_ms = cfg.worker.audit_poll_interval_ms;
    let ingestion_poll_ms = cfg.worker.ingestion_poll_interval_ms;
    let transition_refresh_ms = cfg.worker.transition_refresh_interval_ms;
    let heartbeat_ms = cfg.worker.heartbeat_interval_ms;

    let (shutdown_tx, _) = broadcast::channel::<()>(1);

    let shutdown_tx_signal = shutdown_tx.clone();
    tokio::spawn(async move {
        let _ = tokio::signal::ctrl_c().await;
        tracing::info!("Shutdown signal received");
        let _ = shutdown_tx_signal.send(());
    });

    // Audit worker
    let audit_stream = queue.audit_stream.clone();
    let audit_group = queue.audit_group.clone();
    let audit_dlq = queue.audit_dlq.clone();
    let audit_consumer = consumer_name.clone();
    let mut queue_audit = queue.clone();
    let mut shutdown_rx = shutdown_tx.subscribe();

    let audit_handle = tokio::spawn(async move {
        loop {
            tokio::select! {
                _ = shutdown_rx.recv() => {
                    tracing::info!("Audit worker shutting down");
                    break;
                }
                result = queue_audit.read_batch(&audit_stream, &audit_group, &audit_consumer, 10, audit_poll_ms) => {
                    match result {
                        Ok(messages) => {
                            for (id, payload) in messages {
                                match serde_json::from_str::<AuditJob>(&payload) {
                                    Ok(job) => {
                                        tracing::info!(job_id = %job.job_id, sku_id = %job.sku_id, "Processing audit job");
                                        // TODO: call AuditService::process_audit_job(&pool, job).await
                                        // Only ack after successful processing
                                        if let Err(e) = queue_audit.acknowledge(&audit_stream, &audit_group, &id).await {
                                            tracing::error!("Failed to ack audit job {}: {e}", job.job_id);
                                        }
                                    }
                                    Err(e) => {
                                        tracing::error!("Failed to deserialize audit job: {e}");
                                        match queue_audit.move_to_dlq(&audit_dlq, &payload).await {
                                            Ok(_) => { let _ = queue_audit.acknowledge(&audit_stream, &audit_group, &id).await; }
                                            Err(dlq_err) => tracing::error!("DLQ write failed, message will redeliver: {dlq_err}"),
                                        }
                                    }
                                }
                            }
                        }
                        Err(e) => tracing::warn!("Audit queue read error: {e}"),
                    }
                }
            }
        }
    });

    // Ingestion worker
    let ingestion_stream = queue.ingestion_stream.clone();
    let ingestion_group = queue.ingestion_group.clone();
    let ingestion_dlq = queue.ingestion_dlq.clone();
    let ingestion_consumer = consumer_name.clone();
    let mut queue_ingestion = queue.clone();
    let mut shutdown_rx2 = shutdown_tx.subscribe();

    let ingestion_handle = tokio::spawn(async move {
        loop {
            tokio::select! {
                _ = shutdown_rx2.recv() => {
                    tracing::info!("Ingestion worker shutting down");
                    break;
                }
                result = queue_ingestion.read_batch(&ingestion_stream, &ingestion_group, &ingestion_consumer, 10, ingestion_poll_ms) => {
                    match result {
                        Ok(messages) => {
                            for (id, payload) in messages {
                                match serde_json::from_str::<IngestionJob>(&payload) {
                                    Ok(job) => {
                                        tracing::info!(job_id = %job.job_id, law_ref = %job.law_ref, "Processing ingestion job");
                                        // TODO: call IngestionService::process_job(&pool, job).await
                                        // Only ack after successful processing
                                        if let Err(e) = queue_ingestion.acknowledge(&ingestion_stream, &ingestion_group, &id).await {
                                            tracing::error!("Failed to ack ingestion job {}: {e}", job.job_id);
                                        }
                                    }
                                    Err(e) => {
                                        tracing::error!("Failed to deserialize ingestion job: {e}");
                                        match queue_ingestion.move_to_dlq(&ingestion_dlq, &payload).await {
                                            Ok(_) => { let _ = queue_ingestion.acknowledge(&ingestion_stream, &ingestion_group, &id).await; }
                                            Err(dlq_err) => tracing::error!("DLQ write failed, message will redeliver: {dlq_err}"),
                                        }
                                    }
                                }
                            }
                        }
                        Err(e) => tracing::warn!("Ingestion queue read error: {e}"),
                    }
                }
            }
        }
    });

    // Transition MV refresh
    let pool_refresh = pool.clone();
    let mut shutdown_rx3 = shutdown_tx.subscribe();
    let refresh_handle = tokio::spawn(async move {
        loop {
            tokio::select! {
                _ = shutdown_rx3.recv() => { tracing::info!("Refresh worker shutting down"); break; }
                _ = tokio::time::sleep(Duration::from_millis(transition_refresh_ms)) => {
                    match refresh_mv(&pool_refresh).await {
                        Ok(Some(ms)) => tracing::info!("mv_fiscal_impact refresh complete in {ms}ms"),
                        Ok(None) => tracing::debug!("mv_fiscal_impact refresh skipped (lock held by another worker)"),
                        Err(e) => tracing::error!("mv_fiscal_impact refresh failed: {e}"),
                    }
                }
            }
        }
    });

    // Heartbeat
    let mut shutdown_rx4 = shutdown_tx.subscribe();
    let heartbeat_handle = tokio::spawn(async move {
        loop {
            tokio::select! {
                _ = shutdown_rx4.recv() => { break; }
                _ = tokio::time::sleep(Duration::from_millis(heartbeat_ms)) => {
                    tracing::info!("Worker heartbeat");
                }
            }
        }
    });

    tokio::try_join!(audit_handle, ingestion_handle, refresh_handle, heartbeat_handle)?;
    Ok(())
}

/// Returns Ok(Some(ms)) on successful refresh, Ok(None) when lock is held by another worker.
async fn refresh_mv(pool: &sqlx::PgPool) -> anyhow::Result<Option<u128>> {
    const LOCK_KEY: i64 = 9988776655_i64;
    let locked: bool = sqlx::query_scalar("SELECT pg_try_advisory_lock($1)")
        .bind(LOCK_KEY)
        .fetch_one(pool).await?;

    if !locked {
        return Ok(None);
    }

    let start = std::time::Instant::now();
    let result = sqlx::query("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_fiscal_impact")
        .execute(pool).await;

    let _: bool = sqlx::query_scalar("SELECT pg_advisory_unlock($1)")
        .bind(LOCK_KEY)
        .fetch_one(pool).await
        .unwrap_or(false);

    result?;
    Ok(Some(start.elapsed().as_millis()))
}
