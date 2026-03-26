package br.fiscalbrain.transition

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class TransitionCalendarResponse(
    val years: List<TransitionYearResponse>
)

data class TransitionYearResponse(
    val year: Int,
    @JsonProperty("reform_weight")
    val reformWeight: Double,
    @JsonProperty("legacy_weight")
    val legacyWeight: Double
)

data class EffectiveRateQuery(
    @field:Min(2026)
    @field:Max(2033)
    val year: Int
)

data class BlendedBurdenResponse(
    @JsonProperty("legacy_component")
    val legacyComponent: Double,
    @JsonProperty("reform_component")
    val reformComponent: Double,
    val total: Double,
    val currency: String = "BRL"
)

data class EffectiveRateResponse(
    @JsonProperty("sku_id")
    val skuId: String,
    val year: Int,
    @JsonProperty("blended_burden")
    val blendedBurden: BlendedBurdenResponse
)
