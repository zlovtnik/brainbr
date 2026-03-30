package br.fiscalbrain.pipeline

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration
import kotlin.math.abs

data class AuditGenerationInput(
    val prompt: String,
    val fallbackLawRef: String,
    val fallbackSourceUrl: String,
    val fallbackContent: String,
    val llmModel: String
)

interface EmbeddingProvider {
    fun embed(text: String): List<Double>
    fun embedBatch(texts: List<String>): List<List<Double>> = texts.map { embed(it) }
}

interface AuditModelProvider {
    fun generateAudit(input: AuditGenerationInput): RagOutput
}

@Component
@ConditionalOnProperty(name = ["app.providers.mode"], havingValue = "real", matchIfMissing = true)
class OpenAiModelProvider(
    private val objectMapper: ObjectMapper,
    @Value("\${app.providers.openai.base-url:https://api.openai.com/v1}") private val baseUrl: String,
    @Value("\${app.providers.openai.api-key:}") private val apiKey: String,
    @Value("\${app.models.embedding:text-embedding-3-small}") private val embeddingModel: String
) : EmbeddingProvider, AuditModelProvider {
    private val client: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(5))
                setReadTimeout(Duration.ofSeconds(30))
            }
        )
        .build()

    override fun embed(text: String): List<Double> =
        embedBatch(listOf(text)).first()

    override fun embedBatch(texts: List<String>): List<List<Double>> {
        requireApiKey()
        if (texts.isEmpty()) return emptyList()

        val response = client.post()
            .uri("/embeddings")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                mapOf(
                    "model" to embeddingModel,
                    "input" to texts
                )
            )
            .retrieve()
            .body(JsonNode::class.java)
            ?: throw IllegalStateException("Empty embedding response")

        val data = response.path("data")
        if (!data.isArray || data.size() != texts.size) {
            throw IllegalStateException("Embedding response size mismatch (expected=${texts.size}, got=${data.size()})")
        }
        return data.map { node ->
            val vector = node.path("embedding")
            if (!vector.isArray) throw IllegalStateException("Embedding response missing vector")
            vector.map { it.asDouble() }
        }
    }

    override fun generateAudit(input: AuditGenerationInput): RagOutput {
        requireApiKey()

        val body = mapOf(
            "model" to input.llmModel,
            "temperature" to 0,
            "response_format" to mapOf("type" to "json_object"),
            "messages" to listOf(
                mapOf(
                    "role" to "system",
                    "content" to "You are a fiscal audit engine. Return only valid JSON with keys reform_taxes, audit_confidence, llm_model_used, source."
                ),
                mapOf(
                    "role" to "user",
                    "content" to input.prompt
                )
            )
        )

        val response = client.post()
            .uri("/chat/completions")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode::class.java)
            ?: throw IllegalStateException("Empty chat completion response")

        val content = response.path("choices").path(0).path("message").path("content").asText()
        if (content.isBlank()) {
            throw IllegalStateException("LLM response content is empty")
        }

        val responseId = response.path("id").asText("")
        val parsed = try {
            objectMapper.readTree(content)
        } catch (ex: JsonProcessingException) {
            throw IllegalStateException(
                "Failed to parse LLM JSON response (model=${input.llmModel}, response_id=$responseId)",
                ex
            )
        }
        
        val reformTaxesNode = parsed.path("reform_taxes")
        val reformTaxes: Map<String, Any> = if (reformTaxesNode.isObject) {
            objectMapper.convertValue(reformTaxesNode, object : com.fasterxml.jackson.core.type.TypeReference<Map<String, Any>>() {})
        } else {
            emptyMap()
        }
        
        if (!parsed.hasNonNull("audit_confidence")) {
            throw IllegalStateException(
                "LLM response missing audit_confidence (model=${input.llmModel}, response_id=$responseId)"
            )
        }

        return RagOutput(
            reformTaxes = reformTaxes,
            auditConfidence = parsed.path("audit_confidence").asDouble(),
            llmModelUsed = parsed.path("llm_model_used").asText(input.llmModel),
            source = RagSource(
                lawRef = parsed.path("source").path("law_ref").asText(input.fallbackLawRef),
                content = parsed.path("source").path("content").asText(input.fallbackContent),
                sourceUrl = parsed.path("source").path("source_url").asText(input.fallbackSourceUrl)
            )
        )
    }

    private fun requireApiKey() {
        if (apiKey.isBlank()) {
            throw IllegalStateException("Missing OpenAI API key. Set APP_PROVIDERS_OPENAI_API_KEY")
        }
    }
}

@Component
@ConditionalOnProperty(name = ["app.providers.mode"], havingValue = "stub")
class DeterministicModelProvider(
    @Value("\${app.models.llm:stub-llm}") private val llmModel: String
) : EmbeddingProvider, AuditModelProvider {
    override fun embed(text: String): List<Double> = embedBatch(listOf(text)).first()

    override fun embedBatch(texts: List<String>): List<List<Double>> =
        texts.map { input ->
            val seed = input.hashCode().toLong()
            val values = ArrayList<Double>(1536)
            for (i in 0 until 1536) {
                val value = (((seed + i * 7919) % 1000).toDouble() / 1000.0)
                values.add(abs(value))
            }
            values
        }

    override fun generateAudit(input: AuditGenerationInput): RagOutput {
        val reformTaxes = mapOf(
            "ibs" to 17.5,
            "cbs" to 8.8,
            "tax_rate" to 26.3,
            "is_taxable" to true,
            "confidence" to 0.85
        )

        return RagOutput(
            reformTaxes = reformTaxes,
            auditConfidence = 0.85,
            llmModelUsed = llmModel,
            source = RagSource(
                lawRef = input.fallbackLawRef,
                content = input.fallbackContent,
                sourceUrl = input.fallbackSourceUrl
            )
        )
    }
}
