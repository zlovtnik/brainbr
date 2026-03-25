package br.fiscalbrain.pipeline

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class IngestionJob(
    @JsonProperty("job_id")
    val jobId: String,
    @JsonProperty("company_id")
    val companyId: UUID,
    @JsonProperty("law_ref")
    val lawRef: String,
    @JsonProperty("law_type")
    val lawType: String,
    @JsonProperty("source_url")
    val sourceUrl: String? = null,
    @JsonProperty("raw_content")
    val rawContent: String? = null,
    @JsonProperty("published_at")
    val publishedAt: LocalDate? = null,
    @JsonProperty("effective_at")
    val effectiveAt: LocalDate? = null,
    val tags: List<String> = emptyList(),
    @JsonProperty("request_id")
    val requestId: String? = null,
    val attempt: Int = 0,
    @JsonProperty("created_at")
    val createdAt: Instant = Instant.now()
)

data class AuditJob(
    @JsonProperty("job_id")
    val jobId: String,
    @JsonProperty("company_id")
    val companyId: UUID,
    @JsonProperty("sku_id")
    val skuId: String,
    @JsonProperty("request_id")
    val requestId: String? = null,
    val attempt: Int = 0,
    @JsonProperty("created_at")
    val createdAt: Instant = Instant.now()
)

data class RagSource(
    @JsonProperty("law_ref")
    val lawRef: String,
    val content: String,
    @JsonProperty("source_url")
    val sourceUrl: String
)

data class RagOutput(
    @JsonProperty("reform_taxes")
    val reformTaxes: Map<String, Any>,
    @JsonProperty("audit_confidence")
    val auditConfidence: Double,
    @JsonProperty("llm_model_used")
    val llmModelUsed: String,
    val source: RagSource
)

data class KnowledgeChunk(
    val id: UUID,
    @JsonProperty("knowledge_id")
    val knowledgeId: UUID,
    @JsonProperty("company_id")
    val companyId: UUID,
    @JsonProperty("chunk_index")
    val chunkIndex: Int,
    val content: String,
    val score: Double,
    val metadata: Map<String, Any>
)

data class KnowledgeDocument(
    val id: UUID,
    @JsonProperty("company_id")
    val companyId: UUID,
    @JsonProperty("law_ref")
    val lawRef: String,
    @JsonProperty("law_type")
    val lawType: String,
    val content: String,
    @JsonProperty("source_url")
    val sourceUrl: String?,
    @JsonProperty("content_hash")
    val contentHash: String?,
    @JsonProperty("content_version")
    val contentVersion: Int
)

data class ChunkWriteInput(
    val chunkIndex: Int,
    val content: String,
    val embedding: List<Double>,
    val metadata: Map<String, Any>
)
