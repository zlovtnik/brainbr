package br.fiscalbrain.splitpayment

import br.fiscalbrain.audit.AuditRepository
import br.fiscalbrain.core.tenant.TenantDbSessionService
import br.fiscalbrain.pipeline.SchemaValidationService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Locale

@Service
class SplitPaymentService(
    private val splitPaymentRepository: SplitPaymentRepository,
    private val tenantDbSessionService: TenantDbSessionService,
    private val schemaValidationService: SchemaValidationService,
    private val auditRepository: AuditRepository,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    @Transactional
    fun create(request: SplitPaymentCreateRequest, requestId: String?): SplitPaymentCreateResponse {
        val companyId = tenantDbSessionService.requireCompanyId()
        tenantDbSessionService.apply(companyId)

        val normalizedCurrency = request.currency.uppercase(Locale.US)
        schemaValidationService.validateSplitPaymentEvent(
            mapOf(
                "sku_id" to request.skuId,
                "event_type" to request.eventType,
                "amount" to request.amount,
                "currency" to normalizedCurrency,
                "idempotency_key" to request.idempotencyKey,
                "timestamp" to request.timestamp.toString(),
                "integration_status" to "queued",
                "integration_metadata" to request.integrationMetadata,
                "event_payload" to request.eventPayload
            )
        )

        val createResult = splitPaymentRepository.createOrGet(
            companyId = companyId,
            request = request,
            normalizedCurrency = normalizedCurrency,
            requestId = requestId
        )

        if (createResult.created) {
            val record = createResult.record
            auditRepository.appendAuditEvent(
                companyId = companyId,
                skuId = record.skuId,
                eventType = "SPLIT_PAYMENT_RECORDED",
                actor = "api",
                requestId = requestId,
                payload = mapOf(
                    "event_id" to record.id.toString(),
                    "event_type" to record.eventType,
                    "amount" to record.amount,
                    "currency" to record.currency,
                    "idempotency_key" to record.idempotencyKey,
                    "timestamp" to record.timestamp.toString(),
                    "integration_status" to record.integrationStatus,
                    "created_at" to record.createdAt.toString()
                )
            )

            applicationEventPublisher.publishEvent(
                SplitPaymentCreatedEvent(
                    integrationEvent = SplitPaymentIntegrationEvent(
                        eventId = record.id.toString(),
                        companyId = companyId.toString(),
                        skuId = record.skuId,
                        eventType = record.eventType,
                        amount = record.amount,
                        currency = record.currency,
                        idempotencyKey = record.idempotencyKey,
                        timestamp = record.timestamp,
                        integrationStatus = record.integrationStatus,
                        eventPayload = record.eventPayload,
                        createdAt = record.createdAt
                    )
                )
            )
        }

        return SplitPaymentCreateResponse(
            eventId = createResult.record.id.toString(),
            status = if (createResult.created) "created" else "duplicate",
            integrationStatus = createResult.record.integrationStatus,
            createdAt = createResult.record.createdAt
        )
    }

    @Transactional(readOnly = true)
    fun list(query: SplitPaymentListQuery): SplitPaymentListResponse {
        val companyId = tenantDbSessionService.requireCompanyId()
        tenantDbSessionService.apply(companyId)

        val records = splitPaymentRepository.list(
            companyId = companyId,
            page = query.page,
            limit = query.limit,
            skuId = query.skuId,
            eventType = query.eventType
        )
        val totalCount = splitPaymentRepository.count(companyId, query.skuId, query.eventType)

        return SplitPaymentListResponse(
            items = records.map { record ->
                SplitPaymentEventResponse(
                    eventId = record.id.toString(),
                    skuId = record.skuId,
                    eventType = record.eventType,
                    amount = record.amount,
                    currency = record.currency,
                    idempotencyKey = record.idempotencyKey,
                    timestamp = record.timestamp,
                    integrationStatus = record.integrationStatus,
                    integrationMetadata = record.integrationMetadata,
                    eventPayload = record.eventPayload,
                    createdAt = record.createdAt
                )
            },
            totalCount = totalCount,
            page = query.page,
            limit = query.limit,
            hasMore = query.page * query.limit < totalCount
        )
    }
}
