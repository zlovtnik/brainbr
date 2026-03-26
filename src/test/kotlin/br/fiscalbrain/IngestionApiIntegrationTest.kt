package br.fiscalbrain

import br.fiscalbrain.pipeline.IngestionJob
import br.fiscalbrain.queue.IngestionQueuePublisher
import org.hamcrest.Matchers.emptyString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mockito.reset
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    properties = [
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "app.security.jwt.jwk-set-uri=http://localhost:8081/.well-known/jwks.json"
    ]
)
@AutoConfigureMockMvc
class IngestionApiIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @MockBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockBean
    private lateinit var ingestionQueuePublisher: IngestionQueuePublisher

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DELETE FROM fiscal_audit_log")
        jdbcTemplate.execute("DELETE FROM split_payment_events")
        jdbcTemplate.execute("DELETE FROM audit_explainability_run")
        jdbcTemplate.execute("DELETE FROM inventory_transition")
        jdbcTemplate.execute("DELETE FROM fiscal_knowledge_chunk")
        jdbcTemplate.execute("DELETE FROM fiscal_knowledge_base")
        jdbcTemplate.execute("DELETE FROM companies")
        jdbcTemplate.update(
            "INSERT INTO companies (id, name, cnpj, plan, is_active) VALUES (?, ?, ?, ?, ?)",
            UUID.fromString(TENANT_A),
            "Tenant A",
            "11111111000101",
            "starter",
            true
        )
        jdbcTemplate.update(
            "INSERT INTO companies (id, name, cnpj, plan, is_active) VALUES (?, ?, ?, ?, ?)",
            UUID.fromString(TENANT_B),
            "Tenant B",
            "11111111000102",
            "starter",
            true
        )

        reset(ingestionQueuePublisher)

        given(jwtDecoder.decode(org.mockito.ArgumentMatchers.anyString())).willAnswer { invocation ->
            decodeToken(invocation.getArgument(0))
        }
    }

    @Test
    fun `should reject missing token`() {
        mockMvc.perform(
            post("/api/v1/ingestion/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRawPayload())
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.request_id").exists())
    }

    @Test
    fun `should reject forbidden scope`() {
        mockMvc.perform(
            post("/api/v1/ingestion/jobs")
                .header("Authorization", "Bearer tenant-a-read-only")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRawPayload())
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error_code").value("FORBIDDEN"))
    }

    @Test
    fun `should reject when both source_url and raw_content provided`() {
        mockMvc.perform(
            post("/api/v1/ingestion/jobs")
                .header("Authorization", "Bearer tenant-a-ingestion-write")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "law_ref": "LC-123-2025",
                      "law_type": "complementary_law",
                      "source_url": "https://example.com/law",
                      "raw_content": "full text"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error_code").value("BAD_REQUEST"))
    }

    @Test
    fun `should reject when neither source_url nor raw_content provided`() {
        mockMvc.perform(
            post("/api/v1/ingestion/jobs")
                .header("Authorization", "Bearer tenant-a-ingestion-write")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "law_ref": "LC-123-2025",
                      "law_type": "complementary_law"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error_code").value("BAD_REQUEST"))
    }

    @Test
    fun `should accept valid raw content payload`() {
        mockMvc.perform(
            post("/api/v1/ingestion/jobs")
                .header("Authorization", "Bearer tenant-a-ingestion-write")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRawPayload())
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.job_id", not(emptyString())))
            .andExpect(jsonPath("$.status").value("queued"))
    }

    @Test
    fun `should publish job with tenant company id`() {
        mockMvc.perform(
            post("/api/v1/ingestion/jobs")
                .header("Authorization", "Bearer tenant-a-ingestion-write")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRawPayload())
        )
            .andExpect(status().isAccepted)

        val captor = ArgumentCaptor.forClass(IngestionJob::class.java)
        org.mockito.Mockito.verify(ingestionQueuePublisher).enqueue(captor.capture())

        val job = captor.value
        assertEquals(UUID.fromString(TENANT_A), job.companyId)
        assertEquals("LC-123-2025", job.lawRef)
    }

    private fun validRawPayload(): String =
        """
        {
          "law_ref": "LC-123-2025",
          "law_type": "complementary_law",
          "raw_content": "Conteudo integral"
        }
        """.trimIndent()

    private fun decodeToken(token: String): Jwt {
        val claims: MutableMap<String, Any> = when (token) {
            "tenant-a-ingestion-write" -> mutableMapOf(
                "sub" to "user-a",
                "scope" to "ingestion:write",
                "tenant_id" to TENANT_A
            )
            "tenant-a-read-only" -> mutableMapOf(
                "sub" to "user-a",
                "scope" to "inventory:read",
                "tenant_id" to TENANT_A
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
            registry.add("spring.flyway.user", postgres::getUsername)
            registry.add("spring.flyway.password", postgres::getPassword)
            registry.add("app.providers.mode") { "stub" }
        }
    }
}
