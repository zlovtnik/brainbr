package br.fiscalbrain.splitpayment

import br.fiscalbrain.queue.SplitPaymentEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

data class SplitPaymentCreatedEvent(
    val integrationEvent: SplitPaymentIntegrationEvent
)

@Component
class SplitPaymentCreatedEventListener(
    private val splitPaymentEventPublisher: SplitPaymentEventPublisher
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSplitPaymentCreated(event: SplitPaymentCreatedEvent) {
        splitPaymentEventPublisher.publish(event.integrationEvent)
    }
}
