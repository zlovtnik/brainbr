package br.fiscalbrain.worker.routes

import org.apache.camel.builder.RouteBuilder
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("worker")
class WorkerHeartbeatRoute : RouteBuilder() {
    override fun configure() {
        from("timer:fiscalbrainWorkerHeartbeat?period={{app.worker.heartbeat-interval-ms:60000}}")
            .routeId("worker-heartbeat")
            .setBody().constant("fiscalbrain worker heartbeat")
            .log("Worker route alive: \${body}")
    }
}

