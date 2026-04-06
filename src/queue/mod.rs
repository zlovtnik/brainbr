use redis::{aio::ConnectionManager, RedisResult};
use serde::Serialize;

#[derive(Clone)]
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

    pub async fn reclaim_pending(
        &mut self,
        stream: &str,
        group: &str,
        consumer: &str,
        min_idle_ms: u64,
        count: usize,
    ) -> anyhow::Result<Vec<(String, String)>> {
        // XAUTOCLAIM moves idle PEL entries to this consumer and returns them.
        let results: Vec<redis::Value> = redis::cmd("XAUTOCLAIM")
            .arg(stream)
            .arg(group)
            .arg(consumer)
            .arg(min_idle_ms)
            .arg("0-0")
            .arg("COUNT")
            .arg(count)
            .query_async::<Vec<redis::Value>>(&mut self.conn)
            .await
            .or_else(|e| {
                if e.kind() == redis::ErrorKind::TypeError {
                    Ok(vec![])
                } else {
                    Err(e)
                }
            })?;

        // XAUTOCLAIM returns [next-id, [[id, fields], ...], [deleted-ids]]
        let mut messages = Vec::new();
        if let Some(redis::Value::Array(entries)) = results.get(1) {
            for entry in entries {
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
                            tracing::warn!(message_id = %id, "Reclaimed message missing 'payload' — acknowledging");
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
        Ok(messages)
    }

    pub async fn set_retry_delay(&mut self, stream: &str, id: &str, delay_ms: u64) -> anyhow::Result<()> {
        let key = Self::retry_delay_key(stream, id);
        redis::cmd("PSETEX")
            .arg(&key)
            .arg(delay_ms.max(1))
            .arg("1")
            .query_async::<()>(&mut self.conn)
            .await?;
        Ok(())
    }

    pub async fn retry_delay_remaining_ms(&mut self, stream: &str, id: &str) -> anyhow::Result<Option<u64>> {
        let key = Self::retry_delay_key(stream, id);
        let ttl: i64 = redis::cmd("PTTL")
            .arg(&key)
            .query_async(&mut self.conn)
            .await?;
        Ok((ttl > 0).then_some(ttl as u64))
    }

    pub async fn clear_retry_delay(&mut self, stream: &str, id: &str) -> anyhow::Result<()> {
        let key = Self::retry_delay_key(stream, id);
        redis::cmd("DEL")
            .arg(&key)
            .query_async::<()>(&mut self.conn)
            .await?;
        Ok(())
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
            .query_async::<()>(&mut self.conn)
            .await?;
        Ok(())
    }

    pub async fn move_to_dlq(&mut self, dlq: &str, payload: &str) -> anyhow::Result<()> {
        redis::cmd("XADD").arg(dlq).arg("*").arg("payload").arg(payload)
            .query_async::<()>(&mut self.conn)
            .await?;
        Ok(())
    }

    fn retry_delay_key(stream: &str, id: &str) -> String {
        format!("queue:retry-delay:{stream}:{id}")
    }

    fn retry_count_key(stream: &str, id: &str) -> String {
        format!("queue:retry-count:{stream}:{id}")
    }

    pub async fn get_retry_count(&mut self, stream: &str, id: &str) -> anyhow::Result<u32> {
        let key = Self::retry_count_key(stream, id);
        let val: Option<u32> = redis::cmd("GET")
            .arg(&key)
            .query_async(&mut self.conn)
            .await?;
        Ok(val.unwrap_or(0))
    }

    pub async fn incr_retry_count(&mut self, stream: &str, id: &str, ttl_ms: u64) -> anyhow::Result<u32> {
        let key = Self::retry_count_key(stream, id);
        let count: u32 = redis::cmd("INCR")
            .arg(&key)
            .query_async(&mut self.conn)
            .await?;
        // Refresh TTL on each increment so the key expires after the last retry window
        redis::cmd("PEXPIRE")
            .arg(&key)
            .arg(ttl_ms.max(1))
            .query_async::<()>(&mut self.conn)
            .await?;
        Ok(count)
    }

    pub async fn clear_retry_count(&mut self, stream: &str, id: &str) -> anyhow::Result<()> {
        let key = Self::retry_count_key(stream, id);
        redis::cmd("DEL")
            .arg(&key)
            .query_async::<()>(&mut self.conn)
            .await?;
        Ok(())
    }
}
