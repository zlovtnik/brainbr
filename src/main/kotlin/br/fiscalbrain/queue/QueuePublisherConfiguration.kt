package br.fiscalbrain.queue

import br.fiscalbrain.pipeline.AuditJob
import br.fiscalbrain.pipeline.IngestionJob
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.slf4j.LoggerFactory

@Configuration
class QueuePublisherConfiguration {
    private val logger = LoggerFactory.getLogger(QueuePublisherConfiguration::class.java)

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
