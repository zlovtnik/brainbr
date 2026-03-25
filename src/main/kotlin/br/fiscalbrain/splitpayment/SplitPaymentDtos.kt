package br.fiscalbrain.splitpayment

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

data class SplitPaymentCreateRequest(
    @field:NotBlank
    @field:Size(max = 50)
    @JsonProperty("sku_id")
    val skuId: String,
    @field:NotBlank
    @field:Size(max = 80)
    @JsonProperty("event_type")
    val eventType: String,
    @field:Min(0)
    val amount: Long,
    @field:NotBlank
    @field:Pattern(regexp = "^[A-Za-z]{3}$")
    val currency: String,
    @field:NotBlank
    @field:Size(min = 8, max = 128)
    @JsonProperty("idempotency_key")
    val idempotencyKey: String,
    @JsonProperty("timestamp")
    val timestamp: Instant,
    @JsonProperty("integration_metadata")
    val integrationMetadata: Map<String, Any> = emptyMap(),
    @JsonProperty("event_payload")
    val eventPayload: Map<String, Any> = emptyMap()
)

data class SplitPaymentCreateResponse(
    @JsonProperty("event_id")
    val eventId: String,
    val status: String,
    @JsonProperty("integration_status")
    val integrationStatus: String,
    @JsonProperty("created_at")
    val createdAt: Instant
)

data class SplitPaymentEventResponse(
    @JsonProperty("event_id")
    val eventId: String,
    @JsonProperty("sku_id")
    val skuId: String,
    @JsonProperty("event_type")
    val eventType: String,
    val amount: Long,
    val currency: String,
    @JsonProperty("idempotency_key")
    val idempotencyKey: String,
    @JsonProperty("timestamp")
    val timestamp: Instant,
    @JsonProperty("integration_status")
    val integrationStatus: String,
    @JsonProperty("integration_metadata")
    val integrationMetadata: Map<String, Any>,
    @JsonProperty("event_payload")
    val eventPayload: Map<String, Any>,
    @JsonProperty("created_at")
    val createdAt: Instant
)

data class SplitPaymentListResponse(
    val items: List<SplitPaymentEventResponse>,
    @JsonProperty("total_count")
    val totalCount: Long,
    val page: Int,
    val limit: Int,
    @JsonProperty("has_more")
    val hasMore: Boolean
)

data class SplitPaymentIntegrationEvent(
    @JsonProperty("event_id")
    val eventId: String,
    @JsonProperty("company_id")
    val companyId: String,
    @JsonProperty("sku_id")
    val skuId: String,
    @JsonProperty("event_type")
    val eventType: String,
    val amount: Long,
    val currency: String,
    @JsonProperty("idempotency_key")
    val idempotencyKey: String,
    @JsonProperty("timestamp")
    val timestamp: Instant,
    @JsonProperty("integration_status")
    val integrationStatus: String,
    @JsonProperty("event_payload")
    val eventPayload: Map<String, Any>,
    @JsonProperty("created_at")
    val createdAt: Instant
)

data class SplitPaymentListQuery(
    @field:Min(1)
    val page: Int = 1,
    @field:Min(1)
    @field:Max(100)
    val limit: Int = 50,
    @JsonProperty("sku_id")
    val skuId: String? = null,
    @JsonProperty("event_type")
    val eventType: String? = null
)
