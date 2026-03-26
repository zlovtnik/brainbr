package br.fiscalbrain.pipeline

import br.fiscalbrain.core.tenant.TenantDbSessionService
import br.fiscalbrain.queue.IngestionQueuePublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID

class IngestionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class IngestionRequest(
    val companyId: UUID,
    val lawRef: String,
    val lawType: String,
    val sourceUrl: String? = null,
    val rawContent: String? = null,
    val publishedAt: LocalDate? = null,
    val effectiveAt: LocalDate? = null,
    val tags: List<String> = emptyList(),
    val requestId: String? = null
)

@Service
class IngestionService(
    private val chunkingService: ChunkingService,
    private val embeddingProvider: EmbeddingProvider,
    private val knowledgeRepository: KnowledgeRepository,
    private val tenantDbSessionService: TenantDbSessionService,
    private val ingestionQueuePublisher: IngestionQueuePublisher,
    private val scraperClient: ScraperClient
) {
    private data class ValidatedSourceTarget(
        val uri: URI,
        val resolvedAddress: InetAddress
    )

    fun enqueue(request: IngestionRequest): String {
        val job = IngestionJob(
            jobId = UUID.randomUUID().toString(),
            companyId = request.companyId,
            lawRef = request.lawRef,
            lawType = request.lawType,
            sourceUrl = request.sourceUrl,
            rawContent = request.rawContent,
            publishedAt = request.publishedAt,
            effectiveAt = request.effectiveAt,
            tags = request.tags,
            requestId = request.requestId
        )
        ingestionQueuePublisher.enqueue(job)
        return job.jobId
    }

    @Transactional
    fun process(job: IngestionJob): IngestionProcessResult {
        validateInput(job.sourceUrl, job.rawContent)

        val content = job.rawContent?.trim().takeUnless { it.isNullOrBlank() }
            ?: fetchContent(job.sourceUrl!!)
        val contentHash = hashContent(content)

        return tenantDbSessionService.applyAndRunWithResult(job.companyId) {
            val existing = knowledgeRepository.findActiveKnowledgeDocument(job.companyId, job.lawRef)
            if (existing?.contentHash == contentHash) {
                return@applyAndRunWithResult IngestionProcessResult(
                    jobId = job.jobId,
                    knowledgeId = existing.id,
                    status = "no-op",
                    chunkCount = 0
                )
            }

            val metadata = mutableMapOf<String, Any>(
                "tags" to job.tags,
                "content_hash" to contentHash,
                "source" to if (job.sourceUrl != null) "url" else "raw"
            )

            val knowledge = knowledgeRepository.upsertVersionedKnowledge(
                companyId = job.companyId,
                lawRef = job.lawRef,
                lawType = job.lawType,
                content = content,
                sourceUrl = job.sourceUrl,
                publishedAt = job.publishedAt,
                effectiveAt = job.effectiveAt,
                metadata = metadata,
                contentHash = contentHash
            )

            val chunks = chunkingService.chunk(content)
            val embeddings = embeddingProvider.embedBatch(chunks)
            require(embeddings.size == chunks.size) {
                "Embedding count (${embeddings.size}) does not match chunk count (${chunks.size})"
            }
            val chunkInputs = chunks.zip(embeddings).mapIndexed { idx, (chunk, embedding) ->
                ChunkWriteInput(
                    chunkIndex = idx,
                    content = chunk,
                    embedding = embedding,
                    metadata = mapOf(
                        "law_ref" to job.lawRef,
                        "chunk_index" to idx,
                        "content_version" to knowledge.contentVersion
                    )
                )
            }

            knowledgeRepository.replaceChunks(
                knowledgeId = knowledge.id,
                companyId = job.companyId,
                chunks = chunkInputs
            )

            IngestionProcessResult(
                jobId = job.jobId,
                knowledgeId = knowledge.id,
                status = "processed",
                chunkCount = chunkInputs.size
            )
        }
    }

    private fun fetchContent(sourceUrl: String): String {
        val target = validateAndResolveSourceUrl(sourceUrl)
        val pinnedUri = buildPinnedUri(target.uri, target.resolvedAddress)
        val hostHeader = buildHostHeader(target.uri)

        val result = scraperClient.fetchWithRetry(pinnedUri, hostHeader)
        val normalized = result.body.trim()
        if (normalized.isBlank()) throw IngestionException("Fetched source content is empty")
        return normalized
    }

    private fun validateAndResolveSourceUrl(rawSourceUrl: String): ValidatedSourceTarget {
        val uri = try {
            URI(rawSourceUrl.trim())
        } catch (ex: Exception) {
            throw IngestionException("Invalid source_url format", ex)
        }

        val scheme = uri.scheme?.lowercase() ?: throw IngestionException("source_url must include a scheme")
        if (scheme != "http" && scheme != "https") {
            throw IngestionException("source_url scheme must be http or https")
        }

        if (!uri.userInfo.isNullOrBlank()) {
            throw IngestionException("source_url must not include user info")
        }

        val host = uri.host?.lowercase()?.trim()
            ?: throw IngestionException("source_url must include a valid host")

        val deniedHosts = setOf(
            "localhost",
            "metadata",
            "metadata.google.internal",
            "169.254.169.254"
        )
        if (host in deniedHosts) {
            throw IngestionException("source_url host is not allowed")
        }

        val resolvedAddresses = try {
            InetAddress.getAllByName(host)
        } catch (ex: UnknownHostException) {
            throw IngestionException("source_url host could not be resolved", ex)
        }

        if (resolvedAddresses.isEmpty()) {
            throw IngestionException("source_url host could not be resolved")
        }

        val selectedAddress = resolvedAddresses.firstOrNull { !isDeniedAddress(it) }
            ?: throw IngestionException("source_url resolves to a private or restricted address")

        if (isDeniedAddress(selectedAddress)) {
            throw IngestionException("source_url resolves to a private or restricted address")
        }

        return ValidatedSourceTarget(
            uri = uri.normalize(),
            resolvedAddress = selectedAddress
        )
    }

    private fun buildPinnedUri(sourceUri: URI, resolvedAddress: InetAddress): URI {
        val scheme = sourceUri.scheme?.lowercase()
            ?: throw IngestionException("source_url must include a scheme")
        val normalizedIp = normalizeAddressForUri(resolvedAddress)
        val portPart = if (sourceUri.port != -1) ":${sourceUri.port}" else ""
        val pathPart = sourceUri.rawPath?.takeIf { it.isNotBlank() } ?: "/"
        val queryPart = sourceUri.rawQuery?.let { "?$it" } ?: ""
        val pinned = "$scheme://$normalizedIp$portPart$pathPart$queryPart"
        return try {
            URI(pinned)
        } catch (ex: Exception) {
            throw IngestionException("Failed to build pinned source_url", ex)
        }
    }

    private fun buildHostHeader(sourceUri: URI): String {
        val host = sourceUri.host ?: throw IngestionException("source_url must include a valid host")
        val defaultPort = when (sourceUri.scheme?.lowercase()) {
            "http" -> 80
            "https" -> 443
            else -> -1
        }
        return if (sourceUri.port != -1 && sourceUri.port != defaultPort) {
            "$host:${sourceUri.port}"
        } else {
            host
        }
    }

    private fun normalizeAddressForUri(address: InetAddress): String {
        val hostAddress = address.hostAddress.substringBefore('%')
        return if (address is Inet6Address) "[$hostAddress]" else hostAddress
    }

    private fun isDeniedAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isSiteLocalAddress ||
            address.isLinkLocalAddress || address.isMulticastAddress
        ) {
            return true
        }

        val hostAddress = address.hostAddress
        if (hostAddress == "169.254.169.254" || hostAddress == "100.100.100.200" || hostAddress == "169.254.170.2") {
            return true
        }

        if (address is Inet6Address) {
            val firstByte = address.address.firstOrNull()?.toInt() ?: 0
            if ((firstByte and 0xFE) == 0xFC) {
                return true
            }
        }

        return false
    }

    fun validateInput(sourceUrl: String?, rawContent: String?) {
        val hasUrl = !sourceUrl.isNullOrBlank()
        val hasRaw = !rawContent.isNullOrBlank()
        if (hasUrl == hasRaw) {
            throw IngestionException("Exactly one of source_url or raw_content must be provided")
        }
    }

    private fun hashContent(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}

data class IngestionProcessResult(
    val jobId: String,
    val knowledgeId: UUID,
    val status: String,
    val chunkCount: Int
)
