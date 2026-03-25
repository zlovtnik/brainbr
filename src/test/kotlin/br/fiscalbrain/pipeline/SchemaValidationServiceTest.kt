package br.fiscalbrain.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test

class SchemaValidationServiceTest {
    private val validator = SchemaValidationService(ObjectMapper())

    @Test
    fun validateReformTaxes_validPayload_passes() {
        val payload = mapOf(
            "ibs" to 17.5,
            "cbs" to 8.8,
            "tax_rate" to 26.3,
            "is_taxable" to true
        )

        assertDoesNotThrow {
            validator.validateReformTaxes(payload)
        }
    }

    @Test
    fun validateReformTaxes_missingRequiredField_fails() {
        val payload = mapOf(
            "ibs" to 17.5,
            "cbs" to 8.8,
            "is_taxable" to true
        )

        assertThrows<SchemaValidationException> {
            validator.validateReformTaxes(payload)
        }
    }
}
