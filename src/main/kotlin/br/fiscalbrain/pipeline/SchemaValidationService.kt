package br.fiscalbrain.pipeline

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

class SchemaValidationException(message: String) : RuntimeException(message)

@Component
class SchemaValidationService(
    private val objectMapper: ObjectMapper
) {
    private val schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)

    private val legacyTaxesSchema: JsonSchema = loadSchema("schemas/legacy-taxes-v1.schema.json")

    private val reformTaxesSchema: JsonSchema = loadSchema("schemas/reform-taxes-v1.schema.json")

    private val explainabilityArtifactSchema: JsonSchema = loadSchema("schemas/explainability-artifact-v1.schema.json")

    private val splitPaymentEventSchema: JsonSchema = loadSchema("schemas/split-payment-event-v1.schema.json")

    fun validateLegacyTaxes(payload: Any) {
        validate(schema = legacyTaxesSchema, payload = payload, label = "legacy_taxes")
    }

    fun validateReformTaxes(payload: Any) {
        validate(schema = reformTaxesSchema, payload = payload, label = "reform_taxes")
    }

    fun validateRagOutput(payload: Any) {
        val node = objectMapper.valueToTree<JsonNode>(payload)
        val requiredRoot = listOf("reform_taxes", "audit_confidence", "llm_model_used", "source")
        requiredRoot.forEach { field ->
            if (!node.has(field)) {
                throw SchemaValidationException("Schema validation failed for rag_output: missing field $field")
            }
        }

        val sourceNode = node.path("source")
        val requiredSource = listOf("law_ref", "content", "source_url")
        requiredSource.forEach { field ->
            if (!sourceNode.has(field) || sourceNode.path(field).asText().isBlank()) {
                throw SchemaValidationException("Schema validation failed for rag_output.source: missing field $field")
            }
        }
    }

    fun validateExplainabilityArtifact(payload: Any) {
        validate(schema = explainabilityArtifactSchema, payload = payload, label = "explainability_artifact")
    }

    fun validateSplitPaymentEvent(payload: Any) {
        validate(schema = splitPaymentEventSchema, payload = payload, label = "split_payment_event")
    }

    private fun validate(schema: JsonSchema, payload: Any, label: String) {
        val node = objectMapper.valueToTree<JsonNode>(payload)
        val errors = schema.validate(node)
        if (errors.isNotEmpty()) {
            val detail = errors.joinToString(separator = "; ") { it.message }
            throw SchemaValidationException("Schema validation failed for $label: $detail")
        }
    }

    private fun loadSchema(classpathLocation: String): JsonSchema {
        val content = runCatching {
            ClassPathResource(classpathLocation).inputStream.use { it.readBytes().decodeToString() }
        }.getOrElse { ex ->
            throw IllegalStateException("Schema not found on classpath: $classpathLocation", ex)
        }

        return schemaFactory.getSchema(content)
    }
}
