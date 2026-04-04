use fiscalbrain_br::{
    config::AppConfig,
    db,
    queue::RedisQueueClient,
    services::{
        audit::AuditService,
        pipeline::{AuditJob, IngestionJob, IngestionService},
    },
};
use serde::de::DeserializeOwned;
use std::{collections::HashMap, future::Future, sync::Arc, time::Duration};
use tokio::sync::{broadcast, Mutex};
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

    tokio::try_join!(audit_handle, ingestion_handle, refresh_handle, heartbeat_handle)?;
    Ok(())
}

async fn run_worker<J, F, Fut>(
    mut queue: RedisQueueClient,
    stream: String,
    group: String,
    dlq: String,
    consumer: String,
    poll_ms: u64,
    mut shutdown: broadcast::Receiver<()>,
    max_retries: u32,
    base_backoff_ms: u64,
    process: F,
) where
    J: DeserializeOwned,
    F: Fn(J) -> Fut,
    Fut: Future<Output = anyhow::Result<()>>,
{
    // Per-message retry counters: message_id -> attempt count
    let retry_counts: Arc<Mutex<HashMap<String, u32>>> = Arc::new(Mutex::new(HashMap::new()));

    loop {
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
                for (id, payload) in messages {
                    match serde_json::from_str::<J>(&payload) {
                        Ok(job) => {
                            match process(job).await {
                                Ok(()) => {
                                    retry_counts.lock().await.remove(&id);
                                    if let Err(e) = queue.acknowledge(&stream, &group, &id).await {
                                        tracing::error!(stream, "Failed to ack {id}: {e}");
                                    }
                                }
                                Err(e) => {
                                    let attempt = {
                                        let mut counts = retry_counts.lock().await;
                                        let n = counts.entry(id.clone()).or_insert(0);
                                        *n += 1;
                                        *n
                                    };
                                    if attempt >= max_retries {
                                        tracing::error!(stream, id, attempt, "Max retries reached, routing to DLQ: {e}");
                                        retry_counts.lock().await.remove(&id);
                                        match queue.move_to_dlq(&dlq, &payload).await {
                                            Ok(_) => { let _ = queue.acknowledge(&stream, &group, &id).await; }
                                            Err(dlq_err) => tracing::error!(stream, "DLQ write failed: {dlq_err}"),
                                        }
                                    } else {
                                        let jitter = std::time::SystemTime::now()
                                            .duration_since(std::time::UNIX_EPOCH)
                                            .map(|d| d.subsec_nanos() as u64 % base_backoff_ms)
                                            .unwrap_or(0);
                                        let backoff = base_backoff_ms * (1u64 << (attempt - 1).min(6)) + jitter;
                                        tracing::warn!(stream, id, attempt, backoff_ms = backoff, "Job failed, will redeliver: {e}");
                                        tokio::time::sleep(Duration::from_millis(backoff)).await;
                                    }
                                }
                            }
                        }
                        Err(e) => {
                            tracing::error!(stream, "Failed to deserialize job: {e}");
                            match queue.move_to_dlq(&dlq, &payload).await {
                                Ok(_) => { let _ = queue.acknowledge(&stream, &group, &id).await; }
                                Err(dlq_err) => tracing::error!(stream, "DLQ write failed, message will redeliver: {dlq_err}"),
                            }
                        }
                    }
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
