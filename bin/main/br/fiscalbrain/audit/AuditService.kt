package br.fiscalbrain.audit

import br.fiscalbrain.core.config.AppSettings
import br.fiscalbrain.core.tenant.TenantDbSessionService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import br.fiscalbrain.pipeline.AuditGenerationInput
import br.fiscalbrain.pipeline.AuditJob
import br.fiscalbrain.pipeline.AuditModelProvider
import br.fiscalbrain.pipeline.EmbeddingProvider
import br.fiscalbrain.pipeline.KnowledgeRepository
import br.fiscalbrain.pipeline.RagOutput
import br.fiscalbrain.pipeline.SchemaValidationService
import br.fiscalbrain.queue.AuditQueuePublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.UUID

@Service
class AuditService(
    private val auditRepository: AuditRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val schemaValidationService: SchemaValidationService,
    private val embeddingProvider: EmbeddingProvider,
    private val auditModelProvider: AuditModelProvider,
    private val tenantDbSessionService: TenantDbSessionService,
    private val auditQueuePublisher: AuditQueuePublisher,
    private val appSettings: AppSettings,
    objectMapper: ObjectMapper
) {
    private val canonicalObjectMapper = objectMapper.copy()
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)

    companion object {
        const val EXPLAINABILITY_ARTIFACT_VERSION = "explainability-artifact-v1"
        const val EXPLAINABILITY_SCHEMA_VERSION = "1.0.0"
    }

    @Transactional
    fun enqueueSkuAudit(skuId: String, requestId: String?): ReAuditResponse {
        val companyId = tenantDbSessionService.requireCompanyId()
        tenantDbSessionService.apply(companyId)
        auditRepository.findSku(companyId, skuId) ?: throw AuditNotFoundException("SKU $skuId not found")

        val job = AuditJob(
            jobId = UUID.randomUUID().toString(),
            companyId = companyId,
            skuId = skuId,
            requestId = requestId
        )
        auditQueuePublisher.enqueue(job)
        return ReAuditResponse(jobId = job.jobId, status = "queued")
    }

    @Transactional(readOnly = true)
    fun query(request: AuditQueryRequest): AuditQueryResponse {
        val companyId = tenantDbSessionService.requireCompanyId()
        tenantDbSessionService.apply(companyId)
        val queryEmbedding = embeddingProvider.embed(request.query)
        val publishedAfter = parseDate(request.filters?.publishedAfter)

        val chunks = knowledgeRepository.queryChunks(
            companyId = companyId,
            queryEmbedding = queryEmbedding,
            topK = request.k,
            lawType = request.filters?.lawType,
            publishedAfter = publishedAfter
        )

        return AuditQueryResponse(
            results = chunks.map { chunk ->
                AuditQueryResult(
                    id = chunk.id.toString(),
                    title = chunk.metadata["law_ref"]?.toString() ?: "legal_chunk",
                    content = chunk.content,
                    metadata = chunk.metadata,
                    score = chunk.score
                )
            }
        )
    }

    @Transactional(readOnly = true)
    fun explain(skuId: String): AuditExplainResponse {
        val companyId = tenantDbSessionService.requireCompanyId()
        tenantDbSessionService.apply(companyId)
        val record = auditRepository.findExplain(companyId, skuId) ?: throw AuditNotFoundException("SKU $skuId not found")
        if (record.chunkId == null) {
            throw AuditNotFoundException("No audit result found for SKU $skuId")
        }

        return AuditExplainResponse(
            skuId = record.skuId,
            reformTaxes = record.reformTaxes,
            auditConfidence = record.auditConfidence,
            llmModelUsed = record.llmModelUsed,
            source = AuditExplainSource(
                lawRef = record.lawRef,
                content = record.sourceContent,
                sourceUrl = record.sourceUrl
            )
        )
    }

    @Transactional(readOnly = true)
    fun explainLatestArtifact(skuId: String): AuditExplainabilityArtifactResponse {
        val companyId = tenantDbSessionService.requireCompanyId()
        tenantDbSessionService.apply(companyId)
        val record = auditRepository.findLatestExplainabilityRun(companyId, skuId)
            ?: throw AuditNotFoundException("No explainability artifact found for SKU $skuId")
        return toExplainabilityArtifactResponse(record)
    }

    @Transactional(readOnly = true)
    fun explainArtifactByRunId(runId: String): AuditExplainabilityArtifactResponse {
        val parsedRunId = runCatching { UUID.fromString(runId) }
            .getOrElse { throw IllegalArgumentException("Invalid run_id format") }
        val companyId = tenantDbSessionService.requireCompanyId()
        tenantDbSessionService.apply(companyId)
        val record = auditRepository.findExplainabilityRun(companyId, parsedRunId)
            ?: throw AuditNotFoundException("Explainability run $runId not found")
        return toExplainabilityArtifactResponse(record)
    }

    @Transactional
    fun processAuditJob(job: AuditJob) {
        tenantDbSessionService.applyAndRunWithResult(job.companyId) {
            val sku = auditRepository.findSku(job.companyId, job.skuId)
                ?: throw AuditNotFoundException("SKU ${job.skuId} not found")

            val retrievalQuery = buildRetrievalQuery(sku)
            val topK = 5
            val queryEmbedding = embeddingProvider.embed(retrievalQuery)
            val chunks = knowledgeRepository.queryChunks(
                companyId = job.companyId,
                queryEmbedding = queryEmbedding,
                topK = topK,
                lawType = null,
                publishedAfter = null
            )

            if (chunks.isEmpty()) {
                throw AuditProcessingException("No knowledge chunks found for tenant ${job.companyId}")
            }

            val topChunk = chunks.first()
            val topSource = knowledgeRepository.findChunkSource(topChunk.id, job.companyId)
                ?: throw AuditProcessingException("Missing source for selected chunk ${topChunk.id}")

            val prompt = buildAuditPrompt(sku, chunks.map { it.content })
            val ragOutput = auditModelProvider.generateAudit(
                AuditGenerationInput(
                    prompt = prompt,
                    fallbackLawRef = topSource.lawRef,
                    fallbackSourceUrl = topSource.sourceUrl,
                    fallbackContent = topSource.content,
                    llmModel = appSettings.models.llm
                )
            )

            validatePayloads(sku.legacyTaxes, ragOutput)

            val createdAt = OffsetDateTime.now(ZoneOffset.UTC)
            val sourceSnapshot = mapOf(
                "law_ref" to topSource.lawRef,
                "content" to topSource.content,
                "source_url" to topSource.sourceUrl
            )
            val replayContext = mapOf(
                "retrieval_query" to retrievalQuery,
                "top_k" to topK,
                "selected_chunk_id" to topChunk.id.toString(),
                "candidate_chunks" to chunks.map { chunk ->
                    mapOf(
                        "chunk_id" to chunk.id.toString(),
                        "law_ref" to (chunk.metadata["law_ref"]?.toString() ?: topSource.lawRef),
                        "score" to chunk.score
                    )
                }
            )
            val ragOutputPayload = mapOf(
                "reform_taxes" to ragOutput.reformTaxes,
                "audit_confidence" to ragOutput.auditConfidence,
                "llm_model_used" to ragOutput.llmModelUsed,
                "source" to mapOf(
                    "law_ref" to ragOutput.source.lawRef,
                    "content" to ragOutput.source.content,
                    "source_url" to ragOutput.source.sourceUrl
                )
            )
            val artifactPayload = mapOf(
                "sku_id" to sku.skuId,
                "job_id" to job.jobId,
                "request_id" to job.requestId,
                "artifact_version" to EXPLAINABILITY_ARTIFACT_VERSION,
                "schema_version" to EXPLAINABILITY_SCHEMA_VERSION,
                "llm_model_used" to ragOutput.llmModelUsed,
                "vector_id" to topChunk.id.toString(),
                "audit_confidence" to ragOutput.auditConfidence,
                "source_snapshot" to sourceSnapshot,
                "replay_context" to replayContext,
                "rag_output" to ragOutputPayload,
                "created_at" to createdAt.toString()
            )
            schemaValidationService.validateExplainabilityArtifact(artifactPayload)
            val artifactDigest = digestSha256Hex(canonicalize(artifactPayload))
            val runRecord = auditRepository.persistExplainabilityRun(
                companyId = job.companyId,
                skuId = job.skuId,
                jobId = job.jobId,
                requestId = job.requestId,
                artifactVersion = EXPLAINABILITY_ARTIFACT_VERSION,
                schemaVersion = EXPLAINABILITY_SCHEMA_VERSION,
                llmModelUsed = ragOutput.llmModelUsed,
                vectorId = topChunk.id,
                auditConfidence = ragOutput.auditConfidence,
                sourceSnapshot = sourceSnapshot,
                replayContext = replayContext,
                ragOutput = ragOutputPayload,
                artifactDigest = artifactDigest
            )

            auditRepository.persistAuditResult(
                companyId = job.companyId,
                skuId = job.skuId,
                reformTaxes = ragOutput.reformTaxes,
                vectorId = topChunk.id,
                auditConfidence = ragOutput.auditConfidence,
                llmModelUsed = ragOutput.llmModelUsed
            )

            auditRepository.appendAuditEvent(
                companyId = job.companyId,
                skuId = job.skuId,
                eventType = "RATE_GENERATED",
                actor = "worker",
                requestId = job.requestId,
                payload = auditEventPayload(
                    skuId = job.skuId,
                    vectorId = topChunk.id,
                    auditConfidence = ragOutput.auditConfidence,
                    llmModelUsed = ragOutput.llmModelUsed,
                    runId = runRecord.id,
                    artifactVersion = runRecord.artifactVersion,
                    artifactDigest = runRecord.artifactDigest
                ),
                runId = runRecord.id,
                artifactVersion = runRecord.artifactVersion,
                artifactDigest = runRecord.artifactDigest
            )
        }
    }

    private fun validatePayloads(legacyTaxes: Map<String, Any>, ragOutput: RagOutput) {
        schemaValidationService.validateLegacyTaxes(legacyTaxes)
        schemaValidationService.validateReformTaxes(ragOutput.reformTaxes)
        schemaValidationService.validateRagOutput(ragOutput)
    }

    private fun buildRetrievalQuery(sku: AuditSkuRecord): String =
        listOf(
            sku.description,
            "NCM ${sku.ncmCode}",
            "origin ${sku.originState}",
            "destination ${sku.destinationState}",
            "legacy ${sku.legacyTaxes}"
        ).joinToString(" | ")

    private fun buildAuditPrompt(sku: AuditSkuRecord, chunkContents: List<String>): String {
        val context = chunkContents.joinToString(separator = "\n---\n")
        return """
            Analyze the fiscal impact for the SKU and return a JSON object that matches the required schema.
            SKU:
            - sku_id: ${sku.skuId}
            - description: ${sku.description}
            - ncm_code: ${sku.ncmCode}
            - origin_state: ${sku.originState}
            - destination_state: ${sku.destinationState}
            - legacy_taxes: ${sku.legacyTaxes}

            Legal context:
            $context
        """.trimIndent()
    }

    private fun parseDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) {
            return null
        }
        return try {
            LocalDate.parse(raw)
        } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("Invalid date format for published_after. Use YYYY-MM-DD")
        }
    }

    private fun toExplainabilityArtifactResponse(record: ExplainabilityRunRecord): AuditExplainabilityArtifactResponse {
        val source = record.sourceSnapshot
        return AuditExplainabilityArtifactResponse(
            runId = record.id.toString(),
            skuId = record.skuId,
            jobId = record.jobId,
            requestId = record.requestId,
            artifactVersion = record.artifactVersion,
            schemaVersion = record.schemaVersion,
            artifactDigest = record.artifactDigest,
            llmModelUsed = record.llmModelUsed,
            vectorId = record.vectorId.toString(),
            auditConfidence = record.auditConfidence,
            source = AuditExplainSource(
                lawRef = source["law_ref"]?.toString().orEmpty(),
                content = source["content"]?.toString().orEmpty(),
                sourceUrl = source["source_url"]?.toString().orEmpty()
            ),
            replayContext = record.replayContext,
            ragOutput = record.ragOutput,
            createdAt = record.createdAt
        )
    }

    private fun digestSha256Hex(payload: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun canonicalize(input: Any?): String =
        canonicalObjectMapper.writeValueAsString(normalizeForCanonicalization(input))

    private fun normalizeForCanonicalization(input: Any?): Any? = when (input) {
        is Map<*, *> -> input.entries
            .associate { (k, v) -> k.toString() to normalizeForCanonicalization(v) }
            .toSortedMap()
        is List<*> -> input.map { normalizeForCanonicalization(it) }
        else -> input
    }
}
