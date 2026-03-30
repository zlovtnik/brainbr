package br.fiscalbrain.queue

import br.fiscalbrain.audit.AuditService
import br.fiscalbrain.pipeline.AuditJob
import br.fiscalbrain.pipeline.IngestionJob
import br.fiscalbrain.pipeline.IngestionService
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
@Profile("worker")
class QueueWorkerService(
    private val objectMapper: ObjectMapper,
    private val ingestionService: IngestionService,
    private val auditService: AuditService,
    redisStreamQueueClientProvider: ObjectProvider<RedisStreamQueueClient>,
    @Value("\${app.queue.retry.max-attempts:3}") private val maxAttempts: Int,
    @Value("\${app.queue.retry.base-backoff-ms:1000}") private val baseBackoffMs: Long
) {
    private val logger = LoggerFactory.getLogger(QueueWorkerService::class.java)
    private val queueClient: RedisStreamQueueClient? = redisStreamQueueClientProvider.ifAvailable
    private val consumerId = "worker-${Instant.now().toEpochMilli()}"

    fun processIngestionBatch() {
        val client = queueClient ?: return
        val stream = client.ingestionStreamName
        val group = client.ingestionConsumerGroup
        val records = client.readBatch(stream = stream, group = group, consumerName = consumerId)

        records.forEach { record ->
            val payload = record.value
            if (shouldDeferRecord(payload)) {
                client.enqueue(stream, payload)
                client.acknowledge(stream, group, record.id.value)
                return@forEach
            }

            val attempt = extractAttempt(payload)
            val job = runCatching { toIngestionJob(payload, attempt) }.getOrElse {
                withPayloadContext(payload) {
                    logger.error("Invalid ingestion payload identifiers={}", summarizePayload(payload), it)
                }
                publishMalformedRecordToDeadLetterQueue(
                    client = client,
                    dlqStream = client.ingestionDlqName,
                    payload = payload,
                    attempt = attempt,
                    ex = it
                )
                client.acknowledge(stream, group, record.id.value)
                return@forEach
            }

            withLogContext(job.jobId, job.companyId.toString(), job.requestId) {
                runCatching {
                    ingestionService.process(job)
                    logger.info(
                        "Ingestion job processed queue={} attempt={} job_id={} company_id={} request_id={}",
                        stream,
                        attempt,
                        job.jobId,
                        job.companyId,
                        job.requestId ?: ""
                    )
                    client.acknowledge(stream, group, record.id.value)
                }.onFailure { ex ->
                    handleFailure(
                        client = client,
                        stream = stream,
                        group = group,
                        dlqStream = client.ingestionDlqName,
                        recordId = record.id.value,
                        payload = payload,
                        attempt = attempt,
                        ex = ex
                    )
                }
            }
        }
    }

    fun processAuditBatch() {
        val client = queueClient ?: return
        val stream = client.auditStreamName
        val group = client.auditConsumerGroup
        val records = client.readBatch(stream = stream, group = group, consumerName = consumerId)

        records.forEach { record ->
            val payload = record.value
            if (shouldDeferRecord(payload)) {
                client.enqueue(stream, payload)
                client.acknowledge(stream, group, record.id.value)
                return@forEach
            }

            val attempt = extractAttempt(payload)
            val job = runCatching { toAuditJob(payload, attempt) }.getOrElse {
                withPayloadContext(payload) {
                    logger.error("Invalid audit payload identifiers={}", summarizePayload(payload), it)
                }
                publishMalformedRecordToDeadLetterQueue(
                    client = client,
                    dlqStream = client.auditDlqName,
                    payload = payload,
                    attempt = attempt,
                    ex = it
                )
                client.acknowledge(stream, group, record.id.value)
                return@forEach
            }

            withLogContext(job.jobId, job.companyId.toString(), job.requestId) {
                runCatching {
                    auditService.processAuditJob(job)
                    logger.info(
                        "Audit job processed queue={} attempt={} job_id={} company_id={} request_id={}",
                        stream,
                        attempt,
                        job.jobId,
                        job.companyId,
                        job.requestId ?: ""
                    )
                    client.acknowledge(stream, group, record.id.value)
                }.onFailure { ex ->
                    handleFailure(
                        client = client,
                        stream = stream,
                        group = group,
                        dlqStream = client.auditDlqName,
                        recordId = record.id.value,
                        payload = payload,
                        attempt = attempt,
                        ex = ex
                    )
                }
            }
        }
    }

    private fun handleFailure(
        client: RedisStreamQueueClient,
        stream: String,
        group: String,
        dlqStream: String,
        recordId: String,
        payload: Map<String, String>,
        attempt: Int,
        ex: Throwable
    ) {
        val jobId = identifierFromPayload(payload, "job_id")
        val companyId = identifierFromPayload(payload, "company_id")
        val requestId = identifierFromPayload(payload, "request_id")
        logger.error(
            "Queue processing failed queue={} attempt={} job_id={} company_id={} request_id={}",
            stream,
            attempt,
            jobId,
            companyId,
            requestId,
            ex
        )

        val nextAttempt = attempt + 1
        if (nextAttempt >= maxAttempts) {
            val dlqPayload = payload.toMutableMap()
            dlqPayload["error"] = ex.message ?: ex::class.java.simpleName
            dlqPayload["attempt"] = nextAttempt.toString()
            dlqPayload.remove("retry_after")
            dlqPayload["payload"] = withUpdatedAttempt(dlqPayload["payload"], nextAttempt)
            client.enqueue(dlqStream, dlqPayload)
        } else {
            val backoffMs = baseBackoffMs * (1L shl attempt.coerceAtMost(10))
            val retryPayload = payload.toMutableMap()
            retryPayload["attempt"] = nextAttempt.toString()
            retryPayload["retry_after"] = (Instant.now().toEpochMilli() + backoffMs).toString()
            retryPayload["payload"] = withUpdatedAttempt(retryPayload["payload"], nextAttempt)
            client.enqueue(stream, retryPayload)
        }

        client.acknowledge(stream, group, recordId)
    }

    private fun publishMalformedRecordToDeadLetterQueue(
        client: RedisStreamQueueClient,
        dlqStream: String,
        payload: Map<String, String>,
        attempt: Int,
        ex: Throwable
    ) {
        val dlqPayload = payload.toMutableMap()
        dlqPayload["dlq_reason"] = "malformed_payload"
        dlqPayload["error"] = ex.message ?: ex::class.java.simpleName
        dlqPayload["error_type"] = ex::class.java.name
        dlqPayload["attempt"] = attempt.toString()
        dlqPayload["identifiers"] = objectMapper.writeValueAsString(summarizePayload(payload))
        dlqPayload.remove("retry_after")
        dlqPayload["payload"] = withUpdatedAttempt(dlqPayload["payload"], attempt)
        client.enqueue(dlqStream, dlqPayload)
    }

    private fun toAuditJob(payload: Map<String, String>, attemptOverride: Int): AuditJob {
        val payloadJson = payload["payload"]
        if (!payloadJson.isNullOrBlank()) {
            val parsed = objectMapper.readValue(payloadJson, AuditJob::class.java)
            return parsed.copy(attempt = attemptOverride)
        }

        return AuditJob(
            jobId = payload["job_id"] ?: throw IllegalArgumentException("missing job_id"),
            companyId = payload["company_id"]?.let(UUID::fromString)
                ?: throw IllegalArgumentException("missing company_id"),
            skuId = payload["sku_id"] ?: throw IllegalArgumentException("missing sku_id"),
            requestId = payload["request_id"],
            attempt = attemptOverride,
            createdAt = payload["created_at"]?.let { Instant.parse(it) } ?: Instant.now()
        )
    }

    private fun toIngestionJob(payload: Map<String, String>, attemptOverride: Int): IngestionJob {
        val payloadJson = payload["payload"]
        if (!payloadJson.isNullOrBlank()) {
            val parsed = objectMapper.readValue(payloadJson, IngestionJob::class.java)
            return parsed.copy(attempt = attemptOverride)
        }

        return IngestionJob(
            jobId = payload["job_id"] ?: throw IllegalArgumentException("missing job_id"),
            companyId = payload["company_id"]?.let(UUID::fromString)
                ?: throw IllegalArgumentException("missing company_id"),
            lawRef = payload["law_ref"] ?: throw IllegalArgumentException("missing law_ref"),
            lawType = payload["law_type"] ?: throw IllegalArgumentException("missing law_type"),
            sourceUrl = payload["source_url"]?.ifBlank { null },
            rawContent = payload["raw_content"]?.ifBlank { null },
            publishedAt = payload["published_at"]?.takeIf { it.isNotBlank() }?.let(LocalDate::parse),
            effectiveAt = payload["effective_at"]?.takeIf { it.isNotBlank() }?.let(LocalDate::parse),
            tags = parseTags(payload["tags"]),
            requestId = payload["request_id"]?.ifBlank { null },
            attempt = attemptOverride,
            createdAt = payload["created_at"]?.let { Instant.parse(it) } ?: Instant.now()
        )
    }

    private fun parseTags(raw: String?): List<String> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }

        return runCatching {
            objectMapper.readValue(raw, object : TypeReference<List<String>>() {})
        }.getOrElse {
            raw.removePrefix("[").removeSuffix("]")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }
    }

    private fun shouldDeferRecord(payload: Map<String, String>): Boolean {
        val retryAfter = payload["retry_after"]?.toLongOrNull() ?: return false
        return retryAfter > Instant.now().toEpochMilli()
    }

    private fun extractAttempt(payload: Map<String, String>): Int {
        payload["attempt"]?.toIntOrNull()?.let { return it }
        val payloadJson = payload["payload"] ?: return 0
        return runCatching { objectMapper.readTree(payloadJson).path("attempt").asInt(0) }
            .getOrDefault(0)
    }

    private fun withUpdatedAttempt(payloadJson: String?, attempt: Int): String {
        if (payloadJson.isNullOrBlank()) {
            return payloadJson.orEmpty()
        }

        return runCatching {
            val node = objectMapper.readTree(payloadJson)
            if (node is ObjectNode) {
                node.put("attempt", attempt)
                objectMapper.writeValueAsString(node)
            } else {
                payloadJson
            }
        }.getOrDefault(payloadJson)
    }

    private fun summarizePayload(payload: Map<String, String>): Map<String, String> {
        val keys = setOf("job_id", "company_id", "request_id", "attempt", "event_id", "retry_after")
        return payload.filterKeys { it in keys }
    }

    private fun withLogContext(jobId: String?, companyId: String?, requestId: String?, block: () -> Unit) {
        // Save previous MDC values so nested contexts don't lose outer context
        val savedJobId = MDC.get("job_id")
        val savedCompanyId = MDC.get("company_id")
        val savedRequestId = MDC.get("request_id")
        
        try {
            if (!jobId.isNullOrBlank()) {
                MDC.put("job_id", jobId)
            }
            if (!companyId.isNullOrBlank()) {
                MDC.put("company_id", companyId)
            }
            if (!requestId.isNullOrBlank()) {
                MDC.put("request_id", requestId)
            }
            block()
        } finally {
            // Restore previous values instead of unconditional remove
            if (savedJobId != null) {
                MDC.put("job_id", savedJobId)
            } else {
                MDC.remove("job_id")
            }
            if (savedCompanyId != null) {
                MDC.put("company_id", savedCompanyId)
            } else {
                MDC.remove("company_id")
            }
            if (savedRequestId != null) {
                MDC.put("request_id", savedRequestId)
            } else {
                MDC.remove("request_id")
            }
        }
    }

    private fun withPayloadContext(payload: Map<String, String>, block: () -> Unit) {
        withLogContext(
            jobId = identifierFromPayload(payload, "job_id"),
            companyId = identifierFromPayload(payload, "company_id"),
            requestId = identifierFromPayload(payload, "request_id"),
            block = block
        )
    }

    private fun identifierFromPayload(payload: Map<String, String>, key: String): String? {
        payload[key]?.takeIf { it.isNotBlank() }?.let { return it }
        val payloadJson = payload["payload"] ?: return null
        return runCatching {
            objectMapper.readTree(payloadJson).path(key).asText(null)?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
