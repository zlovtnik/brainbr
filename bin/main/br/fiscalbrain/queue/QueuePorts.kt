package br.fiscalbrain.queue

import br.fiscalbrain.pipeline.AuditJob
import br.fiscalbrain.pipeline.IngestionJob
import br.fiscalbrain.splitpayment.SplitPaymentIntegrationEvent

interface IngestionQueuePublisher {
    fun enqueue(job: IngestionJob)
}

interface AuditQueuePublisher {
    fun enqueue(job: AuditJob)
}

interface SplitPaymentEventPublisher {
    fun publish(event: SplitPaymentIntegrationEvent)
}
