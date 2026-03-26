package br.fiscalbrain.transition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TransitionMathTest {
    @Test
    fun `blended burden uses weights`() {
        val weights = TransitionWeights(year = 2026, legacyWeight = 0.90, reformWeight = 0.10)
        val burden = TransitionMath.blendedBurden(
            legacyTaxes = mapOf("icms" to 10.0, "pis" to 2.0),
            reformTaxes = mapOf("tax_rate" to 25.0),
            weights = weights
        )

        assertEquals(10.8, burden.legacyComponent, 0.0001)
        assertEquals(2.5, burden.reformComponent, 0.0001)
        assertEquals(13.3, burden.total, 0.0001)
    }

    @Test
    fun `risk score clamps between 1 and 10`() {
        val score = TransitionMath.computeRiskScore(
            legacyTotal = 20.0,
            reformTotal = 5.0,
            auditConfidence = 0.9
        )

        assertEquals(4, score)
    }
}
