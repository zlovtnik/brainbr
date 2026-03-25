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

    @Test
    fun validateExplainabilityArtifact_validPayload_passes() {
        val payload = mapOf(
            "sku_id" to "SKU-1",
            "job_id" to "job-1",
            "request_id" to "req-1",
            "artifact_version" to "explainability-artifact-v1",
            "schema_version" to "1.0.0",
            "llm_model_used" to "stub-llm",
            "vector_id" to "11111111-1111-1111-1111-111111111111",
            "audit_confidence" to 0.91,
            "source_snapshot" to mapOf(
                "law_ref" to "LC-68-2024-art-12",
                "content" to "Trecho legal",
                "source_url" to "https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp68.htm"
            ),
            "replay_context" to mapOf(
                "retrieval_query" to "query",
                "top_k" to 5,
                "selected_chunk_id" to "11111111-1111-1111-1111-111111111111",
                "candidate_chunks" to listOf(
                    mapOf(
                        "chunk_id" to "11111111-1111-1111-1111-111111111111",
                        "law_ref" to "LC-68-2024-art-12",
                        "score" to 0.9
                    )
                )
            ),
            "rag_output" to mapOf(
                "reform_taxes" to mapOf(
                    "ibs" to 17.5,
                    "cbs" to 8.8,
                    "tax_rate" to 26.3,
                    "is_taxable" to true
                ),
                "audit_confidence" to 0.91,
                "llm_model_used" to "stub-llm",
                "source" to mapOf(
                    "law_ref" to "LC-68-2024-art-12",
                    "content" to "Trecho legal",
                    "source_url" to "https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp68.htm"
                )
            ),
            "created_at" to "2026-03-25T21:00:00Z"
        )

        assertDoesNotThrow {
            validator.validateExplainabilityArtifact(payload)
        }
    }

    @Test
    fun validateSplitPaymentEvent_invalidCurrency_fails() {
        val payload = mapOf(
            "sku_id" to "SKU-1",
            "event_type" to "settlement_requested",
            "amount" to 10050,
            "currency" to "USD",
            "idempotency_key" to "idem-12345678",
            "timestamp" to "2026-03-25T21:00:00Z",
            "integration_status" to "queued",
            "integration_metadata" to mapOf("provider" to "internal"),
            "event_payload" to mapOf("reference" to "abc")
        )

        assertThrows<SchemaValidationException> {
            validator.validateSplitPaymentEvent(payload)
        }
    }

    @Test
    fun validateSplitPaymentEvent_missingTimestamp_fails() {
        val payload = mapOf(
            "sku_id" to "SKU-1",
            "event_type" to "settlement_requested",
            "amount" to 10050,
            "currency" to "BRL",
            "idempotency_key" to "idem-12345678",
            "integration_status" to "queued",
            "integration_metadata" to mapOf("provider" to "internal"),
            "event_payload" to mapOf("reference" to "abc")
        )

        assertThrows<SchemaValidationException> {
            validator.validateSplitPaymentEvent(payload)
        }
    }
}
