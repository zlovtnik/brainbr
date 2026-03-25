package br.fiscalbrain.audit

import br.fiscalbrain.core.config.AppSettings
import br.fiscalbrain.core.tenant.TenantDbSessionService
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
import java.time.LocalDate
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
    private val appSettings: AppSettings
) {
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

    @Transactional
    fun processAuditJob(job: AuditJob) {
        tenantDbSessionService.applyAndRunWithResult(job.companyId) {
            val sku = auditRepository.findSku(job.companyId, job.skuId)
                ?: throw AuditNotFoundException("SKU ${job.skuId} not found")

            val retrievalQuery = buildRetrievalQuery(sku)
            val queryEmbedding = embeddingProvider.embed(retrievalQuery)
            val chunks = knowledgeRepository.queryChunks(
                companyId = job.companyId,
                queryEmbedding = queryEmbedding,
                topK = 5,
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
                    llmModelUsed = ragOutput.llmModelUsed
                )
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
}
