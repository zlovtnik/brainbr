package br.fiscalbrain.queue

import br.fiscalbrain.pipeline.AuditJob
import br.fiscalbrain.pipeline.IngestionJob

interface IngestionQueuePublisher {
    fun enqueue(job: IngestionJob)
}

interface AuditQueuePublisher {
    fun enqueue(job: AuditJob)
}
