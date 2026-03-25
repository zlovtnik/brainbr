package br.fiscalbrain.worker.routes

import br.fiscalbrain.queue.QueueWorkerService
import org.apache.camel.builder.RouteBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("worker")
class IngestionQueueWorkerRoute(
    private val queueWorkerService: QueueWorkerService
) : RouteBuilder() {
    override fun configure() {
        from("timer:ingestionQueuePoller?period={{app.worker.ingestion-poll-interval-ms:2000}}")
            .routeId("ingestion-queue-worker")
            .process { queueWorkerService.processIngestionBatch() }
    }
}
