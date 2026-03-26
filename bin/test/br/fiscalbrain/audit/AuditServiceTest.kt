package br.fiscalbrain.audit

import br.fiscalbrain.core.config.AppSettings
import br.fiscalbrain.core.web.InvalidRequestException
import br.fiscalbrain.core.tenant.TenantDbSessionService
import br.fiscalbrain.pipeline.AuditModelProvider
import br.fiscalbrain.pipeline.EmbeddingProvider
import br.fiscalbrain.pipeline.KnowledgeRepository
import br.fiscalbrain.pipeline.SchemaValidationService
import br.fiscalbrain.queue.AuditQueuePublisher
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.UUID

class AuditServiceTest {
    private val auditRepository = mock(AuditRepository::class.java)
    private val knowledgeRepository = mock(KnowledgeRepository::class.java)
    private val schemaValidationService = mock(SchemaValidationService::class.java)
    private val embeddingProvider = mock(EmbeddingProvider::class.java)
    private val auditModelProvider = mock(AuditModelProvider::class.java)
    private val tenantDbSessionService = mock(TenantDbSessionService::class.java)
    private val auditQueuePublisher = mock(AuditQueuePublisher::class.java)
    private val appSettings = AppSettings(
        models = AppSettings.Models(embedding = "embed-model", llm = "llm-model"),
        worker = AppSettings.Worker()
    )

    private val service = AuditService(
        auditRepository = auditRepository,
        knowledgeRepository = knowledgeRepository,
        schemaValidationService = schemaValidationService,
        embeddingProvider = embeddingProvider,
        auditModelProvider = auditModelProvider,
        tenantDbSessionService = tenantDbSessionService,
        auditQueuePublisher = auditQueuePublisher,
        appSettings = appSettings,
        objectMapper = ObjectMapper()
    )

    @Test
    fun `explainArtifactByRunId should reject invalid run id with InvalidRequestException`() {
        assertThrows(InvalidRequestException::class.java) {
            service.explainArtifactByRunId("not-a-uuid")
        }
    }

    @Test
    fun `query should reject invalid published after format with InvalidRequestException`() {
        val companyId = UUID.randomUUID()
        `when`(tenantDbSessionService.requireCompanyId()).thenReturn(companyId)
        `when`(embeddingProvider.embed("query")).thenReturn(listOf(0.1, 0.2))

        assertThrows(InvalidRequestException::class.java) {
            service.query(
                AuditQueryRequest(
                    query = "query",
                    filters = AuditQueryFilters(publishedAfter = "03-25-2026")
                )
            )
        }
    }
}
