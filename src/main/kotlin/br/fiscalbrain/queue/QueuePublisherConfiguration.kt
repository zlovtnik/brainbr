package br.fiscalbrain.queue

import br.fiscalbrain.pipeline.AuditJob
import br.fiscalbrain.pipeline.IngestionJob
import br.fiscalbrain.splitpayment.SplitPaymentIntegrationEvent
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import org.slf4j.LoggerFactory

@Configuration
class QueuePublisherConfiguration {
    private val logger = LoggerFactory.getLogger(QueuePublisherConfiguration::class.java)

    @Bean
    @ConditionalOnBean(StringRedisTemplate::class)
    fun redisStreamQueueClient(
        redisTemplate: StringRedisTemplate,
        @Value("\${app.queue.stream.ingestion:queue_ingestion}") ingestionStream: String,
        @Value("\${app.queue.stream.audit:queue_audit}") auditStream: String,
        @Value("\${app.queue.stream.reporting:queue_reporting}") reportingStream: String,
        @Value("\${app.queue.stream.ingestion-dlq:queue_ingestion_dlq}") ingestionDlqStream: String,
        @Value("\${app.queue.stream.audit-dlq:queue_audit_dlq}") auditDlqStream: String,
        @Value("\${app.queue.group.ingestion:ingestion-workers}") ingestionGroup: String,
        @Value("\${app.queue.group.audit:audit-workers}") auditGroup: String
    ): RedisStreamQueueClient = RedisStreamQueueClient(
        redisTemplate = redisTemplate,
        ingestionStream = ingestionStream,
        auditStream = auditStream,
        reportingStream = reportingStream,
        ingestionDlqStream = ingestionDlqStream,
        auditDlqStream = auditDlqStream,
        ingestionGroup = ingestionGroup,
        auditGroup = auditGroup
    )

    @Bean
    fun redisIngestionQueuePublisher(
        redisStreamQueueClientProvider: ObjectProvider<RedisStreamQueueClient>,
        objectMapper: ObjectMapper
    ): IngestionQueuePublisher = redisStreamQueueClientProvider.ifAvailable?.let {
        RedisIngestionQueuePublisher(it, objectMapper)
    } ?: object : IngestionQueuePublisher {
        init {
            logger.warn(
                "RedisStreamQueueClient not available, using no-op IngestionQueuePublisher - jobs will be dropped"
            )
        }

        override fun enqueue(job: IngestionJob) = Unit
    }

    @Bean
    fun redisAuditQueuePublisher(
        redisStreamQueueClientProvider: ObjectProvider<RedisStreamQueueClient>,
        objectMapper: ObjectMapper
    ): AuditQueuePublisher = redisStreamQueueClientProvider.ifAvailable?.let {
        RedisAuditQueuePublisher(it, objectMapper)
    } ?: object : AuditQueuePublisher {
        init {
            logger.warn(
                "RedisStreamQueueClient not available, using no-op AuditQueuePublisher - jobs will be dropped"
            )
        }

        override fun enqueue(job: AuditJob) = Unit
    }

    @Bean
    fun redisSplitPaymentEventPublisher(
        redisStreamQueueClientProvider: ObjectProvider<RedisStreamQueueClient>,
        objectMapper: ObjectMapper
    ): SplitPaymentEventPublisher = redisStreamQueueClientProvider.ifAvailable?.let {
        RedisSplitPaymentEventPublisher(it, objectMapper)
    } ?: object : SplitPaymentEventPublisher {
        init {
            logger.warn(
                "RedisStreamQueueClient not available, using no-op SplitPaymentEventPublisher - events will be dropped"
            )
        }

        override fun publish(event: SplitPaymentIntegrationEvent) = Unit
    }
}

private class RedisIngestionQueuePublisher(
    private val queueClient: RedisStreamQueueClient,
    private val objectMapper: ObjectMapper
) : IngestionQueuePublisher {
    override fun enqueue(job: IngestionJob) {
        val payload = mapOf("payload" to objectMapper.writeValueAsString(job))
        queueClient.enqueue(queueClient.ingestionStreamName, payload)
    }
}

private class RedisAuditQueuePublisher(
    private val queueClient: RedisStreamQueueClient,
    private val objectMapper: ObjectMapper
) : AuditQueuePublisher {
    override fun enqueue(job: AuditJob) {
        val payload = mapOf("payload" to objectMapper.writeValueAsString(job))
        queueClient.enqueue(queueClient.auditStreamName, payload)
    }
}

private class RedisSplitPaymentEventPublisher(
    private val queueClient: RedisStreamQueueClient,
    private val objectMapper: ObjectMapper
) : SplitPaymentEventPublisher {
    override fun publish(event: SplitPaymentIntegrationEvent) {
        val payload = mapOf("payload" to objectMapper.writeValueAsString(event))
        queueClient.enqueue(queueClient.reportingStreamName, payload)
    }
}
