package br.fiscalbrain.worker.routes

import br.fiscalbrain.queue.QueueWorkerService
import org.apache.camel.Exchange
import org.apache.camel.LoggingLevel
import org.apache.camel.builder.RouteBuilder
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("worker")
class AuditQueueWorkerRoute(
    private val queueWorkerService: QueueWorkerService
) : RouteBuilder() {
    private val logger = LoggerFactory.getLogger(AuditQueueWorkerRoute::class.java)

    override fun configure() {
        onException(Exception::class.java)
            .maximumRedeliveries(3)
            .redeliveryDelay(1000)
            .useExponentialBackOff()
            .backOffMultiplier(2.0)
            .retryAttemptedLogLevel(LoggingLevel.WARN)
            .handled(true)
            .process { exchange ->
                val ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable::class.java)
                logger.error("Worker route failure route_id=audit-queue-worker", ex)
            }

        from("timer:auditQueuePoller?period={{app.worker.audit-poll-interval-ms:2000}}")
            .routeId("audit-queue-worker")
            .process { queueWorkerService.processAuditBatch() }
    }
}
