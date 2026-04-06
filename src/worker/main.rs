use fiscalbrain_br::{
    config::AppConfig,
    db,
    queue::RedisQueueClient,
    services::{
        audit::AuditService,
        pipeline::{AuditJob, IngestionJob, IngestionService, NonRetryable, ReingestionService},
    },
};
use serde::de::DeserializeOwned;
use std::{future::Future, sync::Arc, time::Duration};
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
    let consumer = format!("worker-{}", Uuid::new_v4());

    let (shutdown_tx, _) = broadcast::channel::<()>(1);
    let tx = shutdown_tx.clone();
    tokio::spawn(async move {
        let _ = tokio::signal::ctrl_c().await;
        tracing::info!("Shutdown signal received");
        let _ = tx.send(());
    });

    let pool_audit = pool.clone();
    let models_audit = cfg.models.clone();
    let audit_handle = tokio::spawn(run_worker::<AuditJob, _, _>(
        queue.clone(),
        queue.audit_stream.clone(),
        queue.audit_group.clone(),
        queue.audit_dlq.clone(),
        consumer.clone(),
        cfg.worker.audit_poll_interval_ms,
        cfg.worker.reclaim_idle_threshold_ms,
        shutdown_tx.subscribe(),
        cfg.queue.retry_max_attempts,
        cfg.queue.retry_base_backoff_ms,
        move |job| {
            let pool = pool_audit.clone();
            let models = models_audit.clone();
            async move { AuditService::process_audit_job(&pool, &models, job).await }
        },
    ));

    let pool_ingestion = pool.clone();
    let models_ingestion = cfg.models.clone();
    let ingestion_handle = tokio::spawn(run_worker::<IngestionJob, _, _>(
        queue.clone(),
        queue.ingestion_stream.clone(),
        queue.ingestion_group.clone(),
        queue.ingestion_dlq.clone(),
        consumer.clone(),
        cfg.worker.ingestion_poll_interval_ms,
        cfg.worker.reclaim_idle_threshold_ms,
        shutdown_tx.subscribe(),
        cfg.queue.retry_max_attempts,
        cfg.queue.retry_base_backoff_ms,
        move |job| {
            let pool = pool_ingestion.clone();
            let models = models_ingestion.clone();
            async move { IngestionService::process_job(&pool, &models, job).await }
        },
    ));

    let pool_refresh = pool.clone();
    let mut shutdown_refresh = shutdown_tx.subscribe();
    let refresh_ms = cfg.worker.transition_refresh_interval_ms;
    let refresh_handle = tokio::spawn(async move {
        loop {
            tokio::select! {
                _ = shutdown_refresh.recv() => { tracing::info!("Refresh worker shutting down"); break; }
                _ = tokio::time::sleep(Duration::from_millis(refresh_ms)) => {
                    match refresh_mv(&pool_refresh).await {
                        Ok(Some(ms)) => tracing::info!("mv_fiscal_impact refresh complete in {ms}ms"),
                        Ok(None) => tracing::debug!("mv_fiscal_impact refresh skipped (lock held by another worker)"),
                        Err(e) => tracing::error!("mv_fiscal_impact refresh failed: {e}"),
                    }
                }
            }
        }
    });

    let pool_reingest = pool.clone();
    let mut queue_reingest = queue.clone();
    let mut shutdown_reingest = shutdown_tx.subscribe();
    let reingest_ms = cfg.worker.reingest_interval_ms;
    let staleness_ms = cfg.worker.reingest_staleness_ms;
    let ingestion_stream = queue.ingestion_stream.clone();
    let reingest_handle = tokio::spawn(async move {
        if reingest_ms == 0 {
            tracing::info!("Re-ingestion scheduler disabled (WORKER_REINGEST_INTERVAL_MS=0)");
            return;
        }
        loop {
            tokio::select! {
                _ = shutdown_reingest.recv() => { tracing::info!("Re-ingestion scheduler shutting down"); break; }
                _ = tokio::time::sleep(Duration::from_millis(reingest_ms)) => {
                    match ReingestionService::enqueue_stale(&pool_reingest, &mut queue_reingest, staleness_ms, &ingestion_stream).await {
                        Ok(Some(n)) => tracing::info!(enqueued = n, "Re-ingestion scan complete"),
                        Ok(None) => tracing::debug!("Re-ingestion scan skipped (lock held by another worker)"),
                        Err(e) => tracing::error!("Re-ingestion scan failed: {e}"),
                    }
                }
            }
        }
    });

    let heartbeat_ms = cfg.worker.heartbeat_interval_ms;
    let mut shutdown_hb = shutdown_tx.subscribe();
    let heartbeat_handle = tokio::spawn(async move {
        loop {
            tokio::select! {
                _ = shutdown_hb.recv() => break,
                _ = tokio::time::sleep(Duration::from_millis(heartbeat_ms)) => tracing::info!("Worker heartbeat"),
            }
        }
    });

    tokio::try_join!(audit_handle, ingestion_handle, refresh_handle, reingest_handle, heartbeat_handle)?;
    Ok(())
}

async fn run_worker<J, F, Fut>(
    mut queue: RedisQueueClient,
    stream: String,
    group: String,
    dlq: String,
    consumer: String,
    poll_ms: u64,
    reclaim_idle_threshold_ms: u64,
    mut shutdown: broadcast::Receiver<()>,
    max_retries: u32,
    base_backoff_ms: u64,
    process: F,
) where
    J: DeserializeOwned,
    F: Fn(J) -> Fut,
    Fut: Future<Output = anyhow::Result<()>>,
{
    const CLAIM_BATCH_SIZE: usize = 10;

    loop {
        match queue.reclaim_pending(&stream, &group, &consumer, reclaim_idle_threshold_ms.max(1), CLAIM_BATCH_SIZE).await {
            Ok(reclaimed) if !reclaimed.is_empty() => {
                if shutdown.try_recv().is_ok() {
                    tracing::info!(stream, "Worker shutting down");
                    break;
                }
                process_messages(
                    &mut queue,
                    reclaimed,
                    &stream,
                    &group,
                    &dlq,
                    max_retries,
                    base_backoff_ms,
                    &process,
                ).await;
            }
            Ok(_) => {}
            Err(e) => tracing::warn!(stream, "Queue reclaim error: {e}"),
        }

        tokio::select! {
            _ = shutdown.recv() => {
                tracing::info!(stream, "Worker shutting down");
                break;
            }
            result = queue.read_batch(&stream, &group, &consumer, 10, poll_ms) => {
                let messages = match result {
                    Ok(m) => m,
                    Err(e) => { tracing::warn!(stream, "Queue read error: {e}"); continue; }
                };
                process_messages(
                    &mut queue,
                    messages,
                    &stream,
                    &group,
                    &dlq,
                    max_retries,
                    base_backoff_ms,
                    &process,
                ).await;
            }
        }
    }
}

async fn process_messages<J, F, Fut>(
    queue: &mut RedisQueueClient,
    messages: Vec<(String, String)>,
    stream: &str,
    group: &str,
    dlq: &str,
    max_retries: u32,
    base_backoff_ms: u64,
    process: &F,
) where
    J: DeserializeOwned,
    F: Fn(J) -> Fut,
    Fut: Future<Output = anyhow::Result<()>>,
{
    for (id, payload) in messages {
        match queue.retry_delay_remaining_ms(stream, &id).await {
            Ok(Some(remaining_ms)) => {
                tracing::debug!(stream, id, remaining_ms, "Skipping job until retry delay expires");
                continue;
            }
            Ok(None) => {}
            Err(e) => tracing::warn!(stream, id, "Failed to read retry delay: {e}"),
        }

        match serde_json::from_str::<J>(&payload) {
            Ok(job) => {
                    tracing::info!(stream, id, "Processing job");
                    match process(job).await {
                Ok(()) => {
                    if let Err(e) = queue.clear_retry_count(stream, &id).await {
                        tracing::warn!(stream, id, "Failed to clear retry count: {e}");
                    }
                    if let Err(e) = queue.clear_retry_delay(stream, &id).await {
                        tracing::warn!(stream, id, "Failed to clear retry delay: {e}");
                    }
                    if let Err(e) = queue.acknowledge(stream, group, &id).await {
                        tracing::error!(stream, "Failed to ack {id}: {e}");
                    }
                }
                Err(e) => {
                    if e.downcast_ref::<NonRetryable>().is_some() {
                        tracing::error!(stream, id, "Non-retryable error, routing to DLQ immediately: {e}");
                        if let Err(ce) = queue.clear_retry_count(stream, &id).await {
                            tracing::warn!(stream, id, "Failed to clear retry count: {ce}");
                        }
                        if let Err(delay_err) = queue.clear_retry_delay(stream, &id).await {
                            tracing::warn!(stream, id, "Failed to clear retry delay: {delay_err}");
                        }
                        match queue.move_to_dlq(dlq, &payload).await {
                            Ok(_) => { let _ = queue.acknowledge(stream, group, &id).await; }
                            Err(dlq_err) => tracing::error!(stream, "DLQ write failed: {dlq_err}"),
                        }
                        continue;
                    }

                    // TTL = max_retries * max backoff window (capped at 6 doublings) + buffer
                    let count_ttl_ms = (max_retries as u64) * base_backoff_ms.max(1) * (1u64 << 6u64.min(max_retries as u64)) * 2;
                    let attempt = match queue.incr_retry_count(stream, &id, count_ttl_ms).await {
                        Ok(n) => n,
                        Err(e) => { tracing::warn!(stream, id, "Failed to increment retry count: {e}"); 1 }
                    };
                    if attempt >= max_retries {
                        tracing::error!(stream, id, attempt, "Max retries reached, routing to DLQ: {e}");
                        if let Err(ce) = queue.clear_retry_count(stream, &id).await {
                            tracing::warn!(stream, id, "Failed to clear retry count: {ce}");
                        }
                        if let Err(delay_err) = queue.clear_retry_delay(stream, &id).await {
                            tracing::warn!(stream, id, "Failed to clear retry delay: {delay_err}");
                        }
                        match queue.move_to_dlq(dlq, &payload).await {
                            Ok(_) => { let _ = queue.acknowledge(stream, group, &id).await; }
                            Err(dlq_err) => tracing::error!(stream, "DLQ write failed: {dlq_err}"),
                        }
                    } else {
                        let jitter = std::time::SystemTime::now()
                            .duration_since(std::time::UNIX_EPOCH)
                            .map(|d| d.subsec_nanos() as u64 % base_backoff_ms.max(1))
                            .unwrap_or(0);
                        let backoff = base_backoff_ms.max(1) * (1u64 << (attempt - 1).min(6)) + jitter;
                        if let Err(delay_err) = queue.set_retry_delay(stream, &id, backoff).await {
                            tracing::warn!(stream, id, "Failed to persist retry delay: {delay_err}");
                        }
                        tracing::warn!(stream, id, attempt, backoff_ms = backoff, "Job failed, will retry after backoff: {e}");
                    }
                } // end Err(e) arm of process
            } // end match process(job)
            } // end Ok(job)
            Err(e) => {
                tracing::error!(stream, "Failed to deserialize job: {e}");
                if let Err(delay_err) = queue.clear_retry_delay(stream, &id).await {
                    tracing::warn!(stream, id, "Failed to clear retry delay: {delay_err}");
                }
                match queue.move_to_dlq(dlq, &payload).await {
                    Ok(_) => { let _ = queue.acknowledge(stream, group, &id).await; }
                    Err(dlq_err) => tracing::error!(stream, "DLQ write failed, message will redeliver: {dlq_err}"),
                }
            }
        }
    }
}

/// Returns Ok(Some(ms)) on successful refresh, Ok(None) when lock is held by another worker.
async fn refresh_mv(pool: &sqlx::PgPool) -> anyhow::Result<Option<u128>> {
    const LOCK_KEY: i64 = 9988776655_i64;
    let locked: bool = sqlx::query_scalar("SELECT pg_try_advisory_lock($1)")
        .bind(LOCK_KEY)
        .fetch_one(pool).await?;

    if !locked { return Ok(None); }

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
