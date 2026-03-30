package br.fiscalbrain.transition

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

data class TransitionWeights(
    val year: Int,
    val legacyWeight: Double,
    val reformWeight: Double
)

data class BlendedBurden(
    val legacyComponent: Double,
    val reformComponent: Double,
    val total: Double
)

object TransitionMath {
    /** Max absolute delta (percentage points) used to normalize risk scoring. */
    private const val DELTA_NORMALIZER = 30.0
    /** Weight applied to tax delta in risk scoring. */
    private const val DELTA_WEIGHT = 0.6
    /** Weight applied to (1 - audit confidence) in risk scoring. */
    private const val CONFIDENCE_WEIGHT = 0.4

    fun blendedBurden(
        legacyTaxes: Map<String, Double>,
        reformTaxes: Map<String, Double>,
        weights: TransitionWeights
    ): BlendedBurden {
        val legacyTotal = legacyTaxes.values.sum()
        val reformTotal = reformTaxes.values.sum()
        val blended = (legacyTotal * weights.legacyWeight) + (reformTotal * weights.reformWeight)
        return BlendedBurden(
            legacyComponent = legacyTotal * weights.legacyWeight,
            reformComponent = reformTotal * weights.reformWeight,
            total = blended
        )
    }

    fun computeRiskScore(
        legacyTotal: Double,
        reformTotal: Double,
        auditConfidence: Double?
    ): Int {
        val delta = abs(reformTotal - legacyTotal)
        val normalizedDelta = min(delta / DELTA_NORMALIZER, 1.0)
        val clampedConfidence = (auditConfidence ?: 0.5).coerceIn(0.0, 1.0)
        val confidenceTerm = 1 - clampedConfidence
        val rawScore = (DELTA_WEIGHT * normalizedDelta + CONFIDENCE_WEIGHT * confidenceTerm) * 10
        val ceiled = ceil(rawScore).toInt()
        return ceiled.coerceIn(1, 10)
    }
}
