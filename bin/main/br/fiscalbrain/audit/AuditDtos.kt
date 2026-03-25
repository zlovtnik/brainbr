package br.fiscalbrain.audit

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class ReAuditResponse(
    @JsonProperty("job_id")
    val jobId: String,
    val status: String
)

data class AuditQueryRequest(
    @field:NotBlank
    val query: String,
    @field:Min(1)
    @field:Max(20)
    val k: Int = 5,
    val filters: AuditQueryFilters? = null
)

data class AuditQueryFilters(
    @JsonProperty("law_type")
    val lawType: String? = null,
    @JsonProperty("published_after")
    val publishedAfter: String? = null
)

data class AuditQueryResponse(
    val results: List<AuditQueryResult>
)

data class AuditQueryResult(
    val id: String,
    val title: String,
    val content: String,
    val metadata: Map<String, Any>,
    val score: Double
)

data class AuditExplainResponse(
    @JsonProperty("sku_id")
    val skuId: String,
    @JsonProperty("reform_taxes")
    val reformTaxes: Map<String, Any>,
    @JsonProperty("audit_confidence")
    val auditConfidence: Double,
    @JsonProperty("llm_model_used")
    val llmModelUsed: String,
    val source: AuditExplainSource
)

data class AuditExplainSource(
    @JsonProperty("law_ref")
    val lawRef: String,
    val content: String,
    @JsonProperty("source_url")
    val sourceUrl: String
)

data class AuditExplainabilityArtifactResponse(
    @JsonProperty("run_id")
    val runId: String,
    @JsonProperty("sku_id")
    val skuId: String,
    @JsonProperty("job_id")
    val jobId: String,
    @JsonProperty("request_id")
    val requestId: String?,
    @JsonProperty("artifact_version")
    val artifactVersion: String,
    @JsonProperty("schema_version")
    val schemaVersion: String,
    @JsonProperty("artifact_digest")
    val artifactDigest: String,
    @JsonProperty("llm_model_used")
    val llmModelUsed: String,
    @JsonProperty("vector_id")
    val vectorId: String,
    @JsonProperty("audit_confidence")
    val auditConfidence: Double,
    val source: AuditExplainSource,
    @JsonProperty("replay_context")
    val replayContext: Map<String, Any>,
    @JsonProperty("rag_output")
    val ragOutput: Map<String, Any>,
    @JsonProperty("created_at")
    val createdAt: Instant
)
