package br.fiscalbrain.inventory

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

data class InventoryWriteRequest(
    @field:NotBlank
    @field:Size(max = 50)
    @JsonProperty("sku_id")
    val skuId: String,
    @field:NotBlank
    @field:Size(max = 1000)
    val description: String,
    @field:NotBlank
    @field:Size(max = 10)
    @JsonProperty("ncm_code")
    val ncmCode: String,
    @field:NotBlank
    @field:Pattern(regexp = "^[A-Z]{2}$")
    @JsonProperty("origin_state")
    val originState: String,
    @field:NotBlank
    @field:Pattern(regexp = "^[A-Z]{2}$")
    @JsonProperty("destination_state")
    val destinationState: String,
    @JsonProperty("legacy_taxes")
    val legacyTaxes: Map<String, Double> = emptyMap()
)

data class InventoryUpdateRequest(
    @field:NotBlank
    @field:Size(max = 1000)
    val description: String,
    @field:NotBlank
    @field:Size(max = 10)
    @JsonProperty("ncm_code")
    val ncmCode: String,
    @field:NotBlank
    @field:Pattern(regexp = "^[A-Z]{2}$")
    @JsonProperty("origin_state")
    val originState: String,
    @field:NotBlank
    @field:Pattern(regexp = "^[A-Z]{2}$")
    @JsonProperty("destination_state")
    val destinationState: String,
    @JsonProperty("legacy_taxes")
    val legacyTaxes: Map<String, Double> = emptyMap()
)

data class InventorySkuResponse(
    @JsonProperty("sku_id")
    val skuId: String,
    val description: String,
    @JsonProperty("ncm_code")
    val ncmCode: String,
    @JsonProperty("origin_state")
    val originState: String,
    @JsonProperty("destination_state")
    val destinationState: String,
    @JsonProperty("legacy_taxes")
    val legacyTaxes: Map<String, Double>,
    @JsonProperty("reform_taxes")
    val reformTaxes: Map<String, Double>,
    @JsonProperty("is_active")
    val isActive: Boolean,
    @JsonProperty("updated_at")
    val updatedAt: Instant
)

data class InventoryListResponse(
    val items: List<InventorySkuResponse>,
    @JsonProperty("total_count")
    val totalCount: Long,
    val page: Int,
    val limit: Int,
    @JsonProperty("has_more")
    val hasMore: Boolean
)

data class InventoryWriteResultResponse(
    @JsonProperty("sku_id")
    val skuId: String,
    val status: String
)

data class InventoryDeleteResponse(
    @JsonProperty("sku_id")
    val skuId: String,
    val status: String
)

data class InventoryListQuery(
    @field:Min(1)
    val page: Int = 1,
    @field:Min(1)
    @field:Max(100)
    val limit: Int = 50,
    @JsonProperty("include_inactive")
    val includeInactive: Boolean = false
)

data class InventoryRecord(
    val skuId: String,
    val description: String,
    val ncmCode: String,
    val originState: String,
    val destinationState: String,
    val legacyTaxes: Map<String, Double>,
    val reformTaxes: Map<String, Double>,
    val isActive: Boolean,
    val updatedAt: Instant
)
