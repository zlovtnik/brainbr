package br.fiscalbrain.inventory

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import java.time.Instant

data class PageQuery(
    @field:Min(1)
    val page: Int = 1,
    @field:Min(1)
    @field:Max(100)
    val limit: Int = 50
)

data class ImpactItem(
    @JsonProperty("sku_id")
    val skuId: String,
    @JsonProperty("ncm_code")
    val ncmCode: String,
    @JsonProperty("legacy_burden")
    val legacyBurden: Double,
    @JsonProperty("reform_burden")
    val reformBurden: Double,
    val delta: Double,
    @JsonProperty("transition_risk_score")
    val transitionRiskScore: Int?,
    @JsonProperty("updated_at")
    val updatedAt: Instant
)

data class ImpactResponse(
    val data: List<ImpactItem>,
    @JsonProperty("total_count")
    val totalCount: Long,
    val page: Int,
    val limit: Int,
    @JsonProperty("has_more")
    val hasMore: Boolean
)

data class RiskQuery(
    @field:Min(1)
    val page: Int = 1,
    @field:Min(1)
    @field:Max(100)
    val limit: Int = 50,
    @JsonProperty("min_transition_risk_score")
    @field:Min(1)
    @field:Max(10)
    val minTransitionRiskScore: Int? = null,
    @JsonProperty("max_transition_risk_score")
    @field:Min(1)
    @field:Max(10)
    val maxTransitionRiskScore: Int? = null,
    @JsonProperty("ncm_code")
    val ncmCode: String? = null,
    @JsonProperty("updated_after")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val updatedAfter: Instant? = null,
    @JsonProperty("sort_dir")
    @field:jakarta.validation.constraints.Pattern(regexp = "^(asc|desc)$", message = "sortDir must be 'asc' or 'desc'")
    val sortDir: String? = "desc"
)

data class RiskItem(
    @JsonProperty("sku_id")
    val skuId: String,
    @JsonProperty("transition_risk_score")
    val transitionRiskScore: Int?,
    @JsonProperty("last_updated")
    val lastUpdated: Instant,
    val metadata: Map<String, Any>
)

data class RiskResponse(
    val items: List<RiskItem>,
    @JsonProperty("total_count")
    val totalCount: Long,
    val page: Int,
    val limit: Int,
    @JsonProperty("has_more")
    val hasMore: Boolean
)
