package br.fiscalbrain.queue

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.connection.stream.StringRecord
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.StreamOperations
import java.time.Duration
import java.util.UUID

class RedisStreamQueueClient(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${app.queue.stream.ingestion:queue_ingestion}") private val ingestionStream: String,
    @Value("\${app.queue.stream.audit:queue_audit}") private val auditStream: String,
    @Value("\${app.queue.stream.reporting:queue_reporting}") private val reportingStream: String,
    @Value("\${app.queue.stream.ingestion-dlq:queue_ingestion_dlq}") private val ingestionDlqStream: String,
    @Value("\${app.queue.stream.audit-dlq:queue_audit_dlq}") private val auditDlqStream: String,
    @Value("\${app.queue.group.ingestion:ingestion-workers}") private val ingestionGroup: String,
    @Value("\${app.queue.group.audit:audit-workers}") private val auditGroup: String
) {
    val ingestionConsumerGroup: String
        get() = ingestionGroup

    val auditConsumerGroup: String
        get() = auditGroup

    val ingestionStreamName: String
        get() = ingestionStream

    val auditStreamName: String
        get() = auditStream

    val reportingStreamName: String
        get() = reportingStream

    val ingestionDlqName: String
        get() = ingestionDlqStream

    val auditDlqName: String
        get() = auditDlqStream

    @PostConstruct
    fun createGroupsIfNeeded() {
        createGroup(ingestionStream, ingestionGroup)
        createGroup(auditStream, auditGroup)
    }

    fun enqueue(stream: String, payload: Map<String, String>): String {
        val record = StringRecord.of(payload).withStreamKey(stream)
        return streamOps().add(record)?.value
            ?: throw IllegalStateException("Failed to enqueue record to stream: $stream")
    }

    fun readBatch(
        stream: String,
        group: String,
        consumerName: String,
        count: Long = 10,
        blockMs: Long = 1000
    ): List<MapRecord<String, String, String>> {
        return streamOps().read(
            Consumer.from(group, consumerName),
            StreamReadOptions.empty().count(count).block(Duration.ofMillis(blockMs)),
            StreamOffset.create(stream, ReadOffset.lastConsumed())
        ) ?: emptyList()
    }

    fun acknowledge(stream: String, group: String, recordId: String) {
        streamOps().acknowledge(stream, group, recordId)
    }

    private fun createGroup(stream: String, group: String) {
        try {
            redisTemplate.execute { connection ->
                connection.streamCommands().xGroupCreate(
                    stream.toByteArray(),
                    group,
                    ReadOffset.from("0"),
                    true
                )
            }
        } catch (e: Exception) {
            val message = e.cause?.message ?: e.message
            if (message?.contains("BUSYGROUP") != true) {
                throw e
            }
            // Consumer group already exists - this is expected
        }
    }

    private fun streamOps(): StreamOperations<String, String, String> {
        return redisTemplate.opsForStream<String, String>()
    }
}
