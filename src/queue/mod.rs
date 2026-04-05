use redis::{aio::ConnectionManager, RedisResult};
use serde::Serialize;

#[derive(Debug, Clone)]
pub struct RedisQueueClient {
    conn: ConnectionManager,
    pub ingestion_stream: String,
    pub audit_stream: String,
    pub reporting_stream: String,
    pub ingestion_dlq: String,
    pub audit_dlq: String,
    pub ingestion_group: String,
    pub audit_group: String,
}

impl RedisQueueClient {
    pub async fn new(redis_url: &str, config: &crate::config::QueueConfig) -> anyhow::Result<Self> {
        let client = redis::Client::open(redis_url)?;
        let conn = ConnectionManager::new(client).await?;
        let mut client = Self {
            conn,
            ingestion_stream: config.stream_ingestion.clone(),
            audit_stream: config.stream_audit.clone(),
            reporting_stream: config.stream_reporting.clone(),
            ingestion_dlq: config.stream_ingestion_dlq.clone(),
            audit_dlq: config.stream_audit_dlq.clone(),
            ingestion_group: config.group_ingestion.clone(),
            audit_group: config.group_audit.clone(),
        };
        client.ensure_groups().await?;
        Ok(client)
    }

    async fn ensure_groups(&mut self) -> anyhow::Result<()> {
        for (stream, group) in [
            (self.ingestion_stream.clone(), self.ingestion_group.clone()),
            (self.audit_stream.clone(), self.audit_group.clone()),
        ] {
            let result: RedisResult<()> = redis::cmd("XGROUP")
                .arg("CREATE").arg(&stream).arg(&group).arg("$").arg("MKSTREAM")
                .query_async(&mut self.conn)
                .await;
            if let Err(e) = result {
                // redis-rs does not expose structured Redis error codes, so we detect
                // BUSYGROUP (consumer group already exists) via substring match on the
                // error string. This is a known workaround. Treat BUSYGROUP as non-fatal.
                if !e.to_string().contains("BUSYGROUP") {
                    return Err(e.into());
                }
            }
        }
        Ok(())
    }

    pub async fn enqueue<T: Serialize>(&mut self, stream: &str, payload: &T) -> anyhow::Result<String> {
        let json = serde_json::to_string(payload)?;
        let id: String = redis::cmd("XADD")
            .arg(stream).arg("*").arg("payload").arg(&json)
            .query_async(&mut self.conn)
            .await?;
        Ok(id)
    }

    pub async fn read_batch(
        &mut self,
        stream: &str,
        group: &str,
        consumer: &str,
        count: usize,
        block_ms: u64,
    ) -> anyhow::Result<Vec<(String, String)>> {
        let results: Vec<redis::Value> = redis::cmd("XREADGROUP")
            .arg("GROUP").arg(group).arg(consumer)
            .arg("COUNT").arg(count)
            .arg("BLOCK").arg(block_ms)
            .arg("STREAMS").arg(stream).arg(">")
            .query_async::<Vec<redis::Value>>(&mut self.conn)
            .await
            .or_else(|e| {
                // BLOCK timeout returns Nil — treat as empty, propagate real errors
                if e.kind() == redis::ErrorKind::TypeError {
                    Ok(vec![])
                } else {
                    Err(e)
                }
            })?;

        let mut messages = Vec::new();
        if let Some(redis::Value::Array(streams)) = results.first() {
            if let Some(redis::Value::Array(stream_data)) = streams.get(1) {
                for entry in stream_data {
                    if let redis::Value::Array(parts) = entry {
                        if let (Some(redis::Value::BulkString(id)), Some(redis::Value::Array(fields))) =
                            (parts.first(), parts.get(1))
                        {
                            let id = String::from_utf8_lossy(id).to_string();
                            let mut payload_found = false;
                            for chunk in fields.chunks(2) {
                                if let [redis::Value::BulkString(k), redis::Value::BulkString(v)] = chunk {
                                    if k == b"payload" {
                                        messages.push((id.clone(), String::from_utf8_lossy(v).to_string()));
                                        payload_found = true;
                                        break;
                                    }
                                }
                            }
                            if !payload_found {
                                tracing::warn!(message_id = %id, "Message missing 'payload' field — acknowledging to remove from PEL");
                                let _ = redis::cmd("XACK")
                                    .arg(stream)
                                    .arg(group)
                                    .arg(&id)
                                    .query_async::<()>(&mut self.conn)
                                    .await;
                            }
                        }
                    }
                }
            }
        }
        Ok(messages)
    }

    pub async fn acknowledge(&mut self, stream: &str, group: &str, id: &str) -> anyhow::Result<()> {
        redis::cmd("XACK").arg(stream).arg(group).arg(id)
            .query_async(&mut self.conn)
            .await?;
        Ok(())
    }

    pub async fn move_to_dlq(&mut self, dlq: &str, payload: &str) -> anyhow::Result<()> {
        redis::cmd("XADD").arg(dlq).arg("*").arg("payload").arg(payload)
            .query_async(&mut self.conn)
            .await?;
        Ok(())
    }
}
