package br.fiscalbrain

import br.fiscalbrain.pipeline.EmbeddingProvider
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.hamcrest.Matchers.matchesPattern
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.Locale
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    properties = [
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "app.security.jwt.jwk-set-uri=http://localhost:8081/.well-known/jwks.json"
    ]
)
@AutoConfigureMockMvc
class InventoryApiIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @MockBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockBean
    private lateinit var embeddingProvider: EmbeddingProvider

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            """
            TRUNCATE TABLE
                split_payment_event_statuses,
                split_payment_events,
                fiscal_audit_log,
                audit_explainability_run
            RESTART IDENTITY CASCADE
            """.trimIndent()
        )
        jdbcTemplate.execute("DELETE FROM inventory_transition")
        jdbcTemplate.execute("DELETE FROM fiscal_knowledge_chunk")
        jdbcTemplate.execute("DELETE FROM fiscal_knowledge_base")
        jdbcTemplate.execute("DELETE FROM companies")
        jdbcTemplate.update(
            "INSERT INTO companies (id, external_tenant_id, name) VALUES (?, ?, ?)",
            UUID.fromString(TENANT_A),
            "tenant-a",
            "Tenant A"
        )
        jdbcTemplate.update(
            "INSERT INTO companies (id, external_tenant_id, name) VALUES (?, ?, ?)",
            UUID.fromString(TENANT_B),
            "tenant-b",
            "Tenant B"
        )

        given(jwtDecoder.decode(anyString())).willAnswer { invocation ->
            decodeToken(invocation.getArgument(0))
        }
        given(embeddingProvider.embed(anyString())).willReturn(buildVector(0.001))
    }

    @Test
    fun `should reject missing token`() {
        mockMvc.perform(get("/api/v1/inventory/sku/SKU-001"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.request_id").exists())
    }

    @Test
    fun `should reject invalid signature token`() {
        mockMvc.perform(
            get("/api/v1/inventory/sku/SKU-001")
                .header("Authorization", "Bearer invalid-signature")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.request_id").exists())
    }

    @Test
    fun `should reject missing tenant claim`() {
        mockMvc.perform(
            get("/api/v1/inventory/sku/SKU-001")
                .header("Authorization", "Bearer missing-tenant")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error_code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").value("Missing or invalid tenant claim"))
    }

    @Test
    fun `should reject forbidden scope`() {
        mockMvc.perform(
            post("/api/v1/inventory/sku")
                .header("Authorization", "Bearer tenant-a-read-only")
                .contentType(MediaType.APPLICATION_JSON)
                .content(writeRequestPayload("SKU-001", "Cerveja"))
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error_code").value("FORBIDDEN"))
    }

    @Test
    fun `should perform full tenant scoped crud with soft delete`() {
        mockMvc.perform(
            post("/api/v1/inventory/sku")
                .header("Authorization", "Bearer tenant-a-read-write")
                .contentType(MediaType.APPLICATION_JSON)
                .content(writeRequestPayload("SKU-001", "Cerveja Pilsen"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sku_id").value("SKU-001"))
            .andExpect(jsonPath("$.status").value("created"))

        mockMvc.perform(
            get("/api/v1/inventory/sku/SKU-001")
                .header("Authorization", "Bearer tenant-a-read-write")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sku_id").value("SKU-001"))
            .andExpect(jsonPath("$.description").value("Cerveja Pilsen"))
            .andExpect(jsonPath("$.is_active").value(true))

        mockMvc.perform(
            get("/api/v1/inventory/sku/SKU-001")
                .header("Authorization", "Bearer tenant-b-read-write")
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error_code").value("SKU_NOT_FOUND"))

        mockMvc.perform(
            put("/api/v1/inventory/sku/SKU-001")
                .header("Authorization", "Bearer tenant-a-read-write")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "description": "Cerveja Pilsen Atualizada",
                      "ncm_code": "22030000",
                      "origin_state": "SP",
                      "destination_state": "RJ",
                      "legacy_taxes": {
                        "icms": 18.0
                      }
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("updated"))

        mockMvc.perform(
            get("/api/v1/inventory/sku")
                .header("Authorization", "Bearer tenant-a-read-write")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total_count").value(1))
            .andExpect(jsonPath("$.items[0].description").value("Cerveja Pilsen Atualizada"))

        mockMvc.perform(
            delete("/api/v1/inventory/sku/SKU-001")
                .header("Authorization", "Bearer tenant-a-read-write")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("deleted"))

        mockMvc.perform(
            get("/api/v1/inventory/sku/SKU-001")
                .header("Authorization", "Bearer tenant-a-read-write")
        )
            .andExpect(status().isNotFound)

        mockMvc.perform(
            get("/api/v1/inventory/sku/SKU-001")
                .header("Authorization", "Bearer tenant-a-read-write")
                .queryParam("include_inactive", "true")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.is_active").value(false))
    }

    @Test
    fun `should support filtered and sorted inventory listing`() {
        mockMvc.perform(
            post("/api/v1/inventory/sku")
                .header("Authorization", "Bearer tenant-a-read-write")
                .contentType(MediaType.APPLICATION_JSON)
                .content(writeRequestPayload("SKU-200", "Produto Beta"))
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/inventory/sku")
                .header("Authorization", "Bearer tenant-a-read-write")
                .contentType(MediaType.APPLICATION_JSON)
                .content(writeRequestPayload("SKU-100", "Produto Alfa"))
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/api/v1/inventory/sku")
                .header("Authorization", "Bearer tenant-a-read-write")
                .queryParam("query", "alfa")
                .queryParam("sort_by", "sku_id")
                .queryParam("sort_order", "asc")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total_count").value(1))
            .andExpect(jsonPath("$.items[0].sku_id").value("SKU-100"))
            .andExpect(jsonPath("$.items[0].description").value("Produto Alfa"))

        mockMvc.perform(
            get("/api/v1/inventory/sku")
                .header("Authorization", "Bearer tenant-a-read-write")
                .queryParam("sort_by", "sku_id")
                .queryParam("sort_order", "asc")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].sku_id").value("SKU-100"))
            .andExpect(jsonPath("$.items[1].sku_id").value("SKU-200"))
    }

    @Test
    fun `should reject invalid inventory sort params`() {
        mockMvc.perform(
            get("/api/v1/inventory/sku")
                .header("Authorization", "Bearer tenant-a-read-write")
                .queryParam("sort_by", "description")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error_code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value(containsString("Invalid sort_by value")))
    }

    @Test
    fun `should preserve request id and keep health endpoint open`() {
        mockMvc.perform(
            get("/api/v1/platform/info")
                .header("X-Request-Id", "req-123")
        )
            .andExpect(status().isOk)
            .andExpect(header().string("X-Request-Id", "req-123"))

        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }

    @Test
    fun `should sanitize invalid request id header`() {
        mockMvc.perform(
            get("/api/v1/platform/info")
                .header("X-Request-Id", " bad\r\nheader\tvalue ")
        )
            .andExpect(status().isOk)
            .andExpect(header().string("X-Request-Id", matchesPattern("^[a-f0-9-]{36}$")))
    }

    @Test
    fun `should enqueue re-audit job with audit trigger scope`() {
        mockMvc.perform(
            post("/api/v1/inventory/sku")
                .header("Authorization", "Bearer tenant-a-read-write")
                .contentType(MediaType.APPLICATION_JSON)
                .content(writeRequestPayload("SKU-RA", "Produto Reaudit"))
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/inventory/sku/SKU-RA/re-audit")
                .header("Authorization", "Bearer tenant-a-audit-trigger")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("queued"))
            .andExpect(jsonPath("$.job_id").exists())
    }

    @Test
    fun `should return explainability payload for audited sku`() {
        val chunkId = seedKnowledgeAndChunk(
            tenantId = TENANT_A,
            lawRef = "LC-68-2024-art-12",
            content = "Trecho legal para explain endpoint",
            sourceUrl = "https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp68.htm",
            vector = buildVector(0.001)
        )
        seedAuditedInventory(
            tenantId = TENANT_A,
            skuId = "SKU-EXP",
            vectorId = chunkId
        )

        mockMvc.perform(
            get("/api/v1/audit/explain/SKU-EXP")
                .header("Authorization", "Bearer tenant-a-audit-read")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sku_id").value("SKU-EXP"))
            .andExpect(jsonPath("$.reform_taxes.tax_rate").value(26.3))
            .andExpect(jsonPath("$.source.law_ref").value("LC-68-2024-art-12"))
    }

    @Test
    fun `should return latest explainability artifact for compliance scope`() {
        val chunkId = seedKnowledgeAndChunk(
            tenantId = TENANT_A,
            lawRef = "LC-68-2024-art-12",
            content = "Trecho legal para artifact endpoint",
            sourceUrl = "https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp68.htm",
            vector = buildVector(0.002)
        )
        seedAuditedInventory(
            tenantId = TENANT_A,
            skuId = "SKU-ART",
            vectorId = chunkId
        )
        val runId = seedExplainabilityRun(
            tenantId = TENANT_A,
            skuId = "SKU-ART",
            chunkId = chunkId
        )

        mockMvc.perform(
            get("/api/v1/audit/explain/SKU-ART/artifact/latest")
                .header("Authorization", "Bearer tenant-a-compliance-read")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.run_id").value(runId.toString()))
            .andExpect(jsonPath("$.sku_id").value("SKU-ART"))
            .andExpect(jsonPath("$.artifact_version").value("explainability-artifact-v1"))
            .andExpect(jsonPath("$.schema_version").value("1.0.0"))
            .andExpect(jsonPath("$.artifact_digest").value(matchesPattern("^[a-f0-9]{64}$")))
            .andExpect(jsonPath("$.vector_id").value(chunkId.toString()))
            .andExpect(jsonPath("$.replay_context.selected_chunk_id").value(chunkId.toString()))
            .andExpect(jsonPath("$.replay_context.top_k").value(5))
            .andExpect(jsonPath("$.source.law_ref").value("LC-68-2024-art-12"))
            .andExpect(jsonPath("$.source.source_url").value("https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp68.htm"))
            .andExpect(jsonPath("$.rag_output.reform_taxes.tax_rate").value(26.3))
            .andExpect(jsonPath("$.rag_output.audit_confidence").value(0.93))

        mockMvc.perform(
            get("/api/v1/audit/explain/SKU-ART/artifact/latest")
                .header("Authorization", "Bearer tenant-b-compliance-read")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `should fetch explainability artifact by run id for tenant`() {
        val chunkId = seedKnowledgeAndChunk(
            tenantId = TENANT_A,
            lawRef = "LC-68-2024-art-12",
            content = "Trecho legal para run id endpoint",
            sourceUrl = "https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp68.htm",
            vector = buildVector(0.003)
        )
        seedAuditedInventory(
            tenantId = TENANT_A,
            skuId = "SKU-RUN",
            vectorId = chunkId
        )
        val runId = seedExplainabilityRun(
            tenantId = TENANT_A,
            skuId = "SKU-RUN",
            chunkId = chunkId
        )

        mockMvc.perform(
            get("/api/v1/audit/explain/artifact/runs/$runId")
                .header("Authorization", "Bearer tenant-a-compliance-read")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.run_id").value(runId.toString()))
            .andExpect(jsonPath("$.sku_id").value("SKU-RUN"))
    }

    @Test
    fun `should execute semantic audit query with tenant scope`() {
        seedKnowledgeAndChunk(
            tenantId = TENANT_A,
            lawRef = "LC-68-2024-art-12",
            content = "Regras de IBS e CBS para bebidas",
            sourceUrl = "https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp68.htm",
            vector = buildVector(0.001)
        )

        mockMvc.perform(
            post("/api/v1/audit/query")
                .header("Authorization", "Bearer tenant-a-audit-query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "query": "Regras de IBS e CBS para bebidas",
                      "k": 5
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.results[0].metadata.law_ref").value("LC-68-2024-art-12"))
    }

    @Test
    fun `should enforce split payment scopes and idempotent writes`() {
        mockMvc.perform(
            post("/api/v1/split-payment/events")
                .header("Authorization", "Bearer tenant-a-audit-read")
                .contentType(MediaType.APPLICATION_JSON)
                .content(splitPaymentPayload("idem-001"))
        )
            .andExpect(status().isForbidden)

        mockMvc.perform(
            post("/api/v1/split-payment/events")
                .header("Authorization", "Bearer tenant-a-split-write")
                .contentType(MediaType.APPLICATION_JSON)
                .content(splitPaymentPayload("idem-001"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("created"))

        mockMvc.perform(
            post("/api/v1/split-payment/events")
                .header("Authorization", "Bearer tenant-a-split-write")
                .contentType(MediaType.APPLICATION_JSON)
                .content(splitPaymentPayload("idem-001"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("duplicate"))

        mockMvc.perform(
            get("/api/v1/split-payment/events")
                .header("Authorization", "Bearer tenant-a-split-read")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total_count").value(1))
            .andExpect(jsonPath("$.items[0].idempotency_key").value("idem-001"))
            .andExpect(jsonPath("$.items[0].amount").value(15045))
            .andExpect(jsonPath("$.items[0].timestamp").exists())

        mockMvc.perform(
            get("/api/v1/split-payment/events")
                .header("Authorization", "Bearer tenant-b-split-read")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total_count").value(0))
    }

    @Test
    fun `should reject invalid split payment pagination`() {
        mockMvc.perform(
            get("/api/v1/split-payment/events")
                .header("Authorization", "Bearer tenant-a-split-read")
                .queryParam("page", "0")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should reject update and delete on immutable audit tables`() {
        val chunkId = seedKnowledgeAndChunk(
            tenantId = TENANT_A,
            lawRef = "LC-68-2024-art-12",
            content = "Trecho legal para immutable test",
            sourceUrl = "https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp68.htm",
            vector = buildVector(0.004)
        )
        seedAuditedInventory(
            tenantId = TENANT_A,
            skuId = "SKU-IMM",
            vectorId = chunkId
        )
        val runId = seedExplainabilityRun(
            tenantId = TENANT_A,
            skuId = "SKU-IMM",
            chunkId = chunkId
        )

        jdbcTemplate.update(
            """
            INSERT INTO fiscal_audit_log (
                company_id, sku_id, event_type, actor, request_id, event_payload, run_id, artifact_version, artifact_digest
            ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
            """.trimIndent(),
            UUID.fromString(TENANT_A),
            "SKU-IMM",
            "RATE_GENERATED",
            "worker",
            "req-imm",
            """{"sku_id":"SKU-IMM"}""",
            runId,
            "explainability-artifact-v1",
            "d".repeat(64)
        )
        val splitEventId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO split_payment_events (
                id, company_id, sku_id, event_type, amount, currency, idempotency_key, event_timestamp,
                integration_status, integration_metadata, event_payload, request_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
            """.trimIndent(),
            splitEventId,
            UUID.fromString(TENANT_A),
            "SKU-IMM",
            "settlement_requested",
            15045L,
            "BRL",
            "idem-immut",
            Instant.parse("2026-03-25T21:00:00Z"),
            "queued",
            """{"provider":"internal"}""",
            """{"order_ref":"ORDER-IMM"}""",
            "req-imm"
        )
        val splitStatusId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO split_payment_event_statuses (
                id, event_id, company_id, status, status_metadata, request_id
            ) VALUES (?, ?, ?, ?, ?::jsonb, ?)
            """.trimIndent(),
            splitStatusId,
            splitEventId,
            UUID.fromString(TENANT_A),
            "queued",
            """{"provider":"internal"}""",
            "req-imm"
        )

        assertThrows<Exception> {
            jdbcTemplate.execute("UPDATE fiscal_audit_log SET event_type = 'MUTATED'")
        }
        assertThrows<Exception> {
            jdbcTemplate.execute("DELETE FROM fiscal_audit_log")
        }
        assertThrows<Exception> {
            jdbcTemplate.execute("UPDATE audit_explainability_run SET schema_version = '2.0.0'")
        }
        assertThrows<Exception> {
            jdbcTemplate.execute("DELETE FROM audit_explainability_run")
        }
        assertThrows<Exception> {
            jdbcTemplate.execute("UPDATE split_payment_events SET integration_status = 'processed'")
        }
        assertThrows<Exception> {
            jdbcTemplate.execute("DELETE FROM split_payment_events")
        }
        assertThrows<Exception> {
            jdbcTemplate.execute("UPDATE split_payment_event_statuses SET status = 'processed'")
        }
        assertThrows<Exception> {
            jdbcTemplate.execute("DELETE FROM split_payment_event_statuses")
        }
    }

    private fun writeRequestPayload(skuId: String, description: String): String = """
        {
          "sku_id": "$skuId",
          "description": "$description",
          "ncm_code": "22030000",
          "origin_state": "SP",
          "destination_state": "RJ",
          "legacy_taxes": {
            "icms": 18.0,
            "pis": 1.65
          }
        }
    """.trimIndent()

    private fun splitPaymentPayload(idempotencyKey: String): String = """
        {
          "sku_id": "SKU-SP-001",
          "event_type": "settlement_requested",
          "amount": 15045,
          "currency": "BRL",
          "idempotency_key": "$idempotencyKey",
          "timestamp": "2026-03-25T21:00:00Z",
          "integration_metadata": {
            "provider": "internal"
          },
          "event_payload": {
            "order_ref": "ORDER-1"
          }
        }
    """.trimIndent()

    private fun decodeToken(token: String): Jwt {
        val claims: MutableMap<String, Any> = when (token) {
            "tenant-a-read-write" -> mutableMapOf(
                "sub" to "user-a",
                "scope" to "inventory:read inventory:write",
                "tenant_id" to TENANT_A
            )
            "tenant-b-read-write" -> mutableMapOf(
                "sub" to "user-b",
                "scope" to "inventory:read inventory:write",
                "tenant_id" to TENANT_B
            )
            "tenant-a-read-only" -> mutableMapOf(
                "sub" to "user-a",
                "scope" to "inventory:read",
                "tenant_id" to TENANT_A
            )
            "tenant-a-audit-trigger" -> mutableMapOf(
                "sub" to "auditor-a",
                "scope" to "audit:trigger",
                "tenant_id" to TENANT_A
            )
            "tenant-a-audit-read" -> mutableMapOf(
                "sub" to "auditor-a",
                "scope" to "audit:read",
                "tenant_id" to TENANT_A
            )
            "tenant-a-audit-query" -> mutableMapOf(
                "sub" to "auditor-a",
                "scope" to "audit:query",
                "tenant_id" to TENANT_A
            )
            "tenant-a-compliance-read" -> mutableMapOf(
                "sub" to "compliance-a",
                "scope" to "compliance:read",
                "tenant_id" to TENANT_A
            )
            "tenant-b-compliance-read" -> mutableMapOf(
                "sub" to "compliance-b",
                "scope" to "compliance:read",
                "tenant_id" to TENANT_B
            )
            "tenant-a-split-write" -> mutableMapOf(
                "sub" to "split-a",
                "scope" to "split_payment:write",
                "tenant_id" to TENANT_A
            )
            "tenant-a-split-read" -> mutableMapOf(
                "sub" to "split-a",
                "scope" to "split_payment:read",
                "tenant_id" to TENANT_A
            )
            "tenant-b-split-read" -> mutableMapOf(
                "sub" to "split-b",
                "scope" to "split_payment:read",
                "tenant_id" to TENANT_B
            )
            "missing-tenant" -> mutableMapOf(
                "sub" to "user-a",
                "scope" to "inventory:read inventory:write"
            )
            else -> throw BadJwtException("Invalid signature")
        }

        return Jwt(
            token,
            Instant.now().minusSeconds(30),
            Instant.now().plusSeconds(600),
            mapOf("alg" to "RS256", "typ" to "JWT"),
            claims
        )
    }

    companion object {
        private const val TENANT_A = "00000000-0000-0000-0000-000000000001"
        private const val TENANT_B = "00000000-0000-0000-0000-000000000002"

        @Container
        private val postgres = PostgreSQLContainer("pgvector/pgvector:pg16")
            .withDatabaseName("fiscalbrain")
            .withUsername("fiscal_user")
            .withPassword("changeme")
            .withInitScript("test-init.sql")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.url", postgres::getJdbcUrl)
            registry.add("spring.flyway.user", postgres::getUsername)
            registry.add("spring.flyway.password", postgres::getPassword)
            registry.add("app.providers.mode") { "stub" }
        }
    }

    private fun seedKnowledgeAndChunk(
        tenantId: String,
        lawRef: String,
        content: String,
        sourceUrl: String,
        vector: List<Double>
    ): UUID {
        val knowledgeId = UUID.randomUUID()
        val chunkId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO fiscal_knowledge_base (
                id, company_id, law_ref, law_type, content, source_url, metadata, content_hash, content_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            """.trimIndent(),
            knowledgeId,
            UUID.fromString(tenantId),
            lawRef,
            "complementary_law",
            content,
            sourceUrl,
            """{"tags":["phase2"]}""",
            "hash-$lawRef",
            1
        )
        jdbcTemplate.update(
            """
            INSERT INTO fiscal_knowledge_chunk (
                id, knowledge_id, company_id, chunk_index, content, embedding, metadata
            ) VALUES (?, ?, ?, ?, ?, ?::vector, ?::jsonb)
            """.trimIndent(),
            chunkId,
            knowledgeId,
            UUID.fromString(tenantId),
            0,
            content,
            vectorLiteral(vector),
            """{"law_ref":"$lawRef"}"""
        )
        return chunkId
    }

    private fun seedAuditedInventory(tenantId: String, skuId: String, vectorId: UUID) {
        jdbcTemplate.update(
            """
            INSERT INTO inventory_transition (
                sku_id, company_id, description, ncm_code, origin_state, destination_state,
                legacy_taxes, reform_taxes, is_active, vector_id, audit_confidence, llm_model_used, last_llm_audit
            ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, TRUE, ?, ?, ?, NOW())
            """.trimIndent(),
            skuId,
            UUID.fromString(tenantId),
            "Produto auditado",
            "22030000",
            "SP",
            "RJ",
            """{"icms":18.0,"pis":1.65,"cofins":7.6,"iss":0.0}""",
            """{"ibs":17.5,"cbs":8.8,"tax_rate":26.3,"is_taxable":true}""",
            vectorId,
            0.93,
            "stub-llm"
        )
    }

    private fun seedExplainabilityRun(tenantId: String, skuId: String, chunkId: UUID): UUID {
        val runId = UUID.randomUUID()
        val ragOutput = """
            {
              "reform_taxes": {"ibs":17.5,"cbs":8.8,"tax_rate":26.3,"is_taxable":true},
              "audit_confidence": 0.93,
              "llm_model_used": "stub-llm",
              "source": {
                "law_ref": "LC-68-2024-art-12",
                "content": "Trecho legal para artifact endpoint",
                "source_url": "https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp68.htm"
              }
            }
        """.trimIndent()
        val sourceSnapshot = """
            {
              "law_ref": "LC-68-2024-art-12",
              "content": "Trecho legal para artifact endpoint",
              "source_url": "https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp68.htm"
            }
        """.trimIndent()
        val replayContext = """
            {
              "retrieval_query": "sample query",
              "top_k": 5,
              "selected_chunk_id": "$chunkId",
              "candidate_chunks": [
                {"chunk_id":"$chunkId","law_ref":"LC-68-2024-art-12","score":0.98}
              ]
            }
        """.trimIndent()

        jdbcTemplate.update(
            """
            INSERT INTO audit_explainability_run (
                id, company_id, sku_id, job_id, request_id, artifact_version, schema_version, llm_model_used,
                vector_id, audit_confidence, source_snapshot, replay_context, rag_output, artifact_digest
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?)
            """.trimIndent(),
            runId,
            UUID.fromString(tenantId),
            skuId,
            "job-$skuId",
            "req-$skuId",
            "explainability-artifact-v1",
            "1.0.0",
            "stub-llm",
            chunkId,
            0.93,
            sourceSnapshot,
            replayContext,
            ragOutput,
            "a".repeat(64)
        )
        return runId
    }

    private fun buildVector(base: Double): List<Double> = List(1536) { base + (it % 10) * 0.0001 }

    private fun vectorLiteral(values: List<Double>): String =
        values.joinToString(prefix = "[", postfix = "]") { String.format(Locale.US, "%.10f", it) }
}
