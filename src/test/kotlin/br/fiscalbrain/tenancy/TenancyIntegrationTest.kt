package br.fiscalbrain.tenancy

import java.util.UUID
import br.fiscalbrain.core.tenant.TenantContextHolder
import br.fiscalbrain.core.tenant.TenantDbSessionService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenancyIntegrationTest {
	@Autowired
	lateinit var jdbcTemplate: JdbcTemplate

	@Autowired
	lateinit var tenantDbSessionService: TenantDbSessionService

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@BeforeEach
	fun setUpTenantA() {
		TenantContextHolder.set(TENANT_A_ID)
		jdbcTemplate.update(
			"INSERT INTO companies (id, external_tenant_id, name) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
			TENANT_A_ID,
			"tenant-a",
			"Tenant A"
		)
		jdbcTemplate.update(
			"INSERT INTO companies (id, external_tenant_id, name) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
			TENANT_B_ID,
			"tenant-b",
			"Tenant B"
		)
		tenantDbSessionService.apply(TENANT_A_ID)
		jdbcTemplate.update(
			"INSERT INTO inventory_transition (company_id, sku_id, description, ncm_code, legacy_taxes, reform_taxes) VALUES (?, 'SKU-1', 'Sample SKU A', '22030000', '{}'::jsonb, '{}'::jsonb) ON CONFLICT DO NOTHING",
			TENANT_A_ID
		)
	}

	@AfterEach
	fun cleanup() {
		TenantContextHolder.clear()
		jdbcTemplate.execute("DELETE FROM inventory_transition")
	}

	@Test
	fun `flyway migrations applied`() {
		val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Long::class.java)
		assertThat(count).isGreaterThanOrEqualTo(2)
	}

	@Test
	fun `health endpoint is up`() {
		val response = restTemplate.getForEntity("/actuator/health", Map::class.java)
		assertThat(response.statusCode.is2xxSuccessful).isTrue()
		assertThat((response.body?.get("status") as? String)).isEqualTo("UP")
	}

	@Test
	fun `rls blocks cross tenant read`() {
		tenantDbSessionService.apply(TENANT_A_ID)
		val rows = jdbcTemplate.queryForList(
			"SELECT sku_id FROM inventory_transition WHERE sku_id = 'SKU-1'"
		)
		assertThat(rows).hasSize(1)

		TenantContextHolder.set(TENANT_B_ID)
		tenantDbSessionService.apply(TENANT_B_ID)
		val otherRows = jdbcTemplate.queryForList(
			"SELECT sku_id FROM inventory_transition WHERE sku_id = 'SKU-1'"
		)
		assertThat(otherRows).isEmpty()
	}

	@Test
	fun `rls blocks cross tenant update`() {
		TenantContextHolder.set(TENANT_B_ID)
		tenantDbSessionService.apply(TENANT_B_ID)
		val updated = jdbcTemplate.update(
			"UPDATE inventory_transition SET description = 'oops' WHERE sku_id = 'SKU-1'"
		)
		assertThat(updated).isZero()
	}

	companion object {
		private val TENANT_A_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
		private val TENANT_B_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")

		@Container
		val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg16")

		@DynamicPropertySource
		@JvmStatic
		fun registerProperties(registry: DynamicPropertyRegistry) {
			registry.add("spring.datasource.url") { postgres.jdbcUrl }
			registry.add("spring.datasource.username") { postgres.username }
			registry.add("spring.datasource.password") { postgres.password }
			registry.add("spring.flyway.url") { postgres.jdbcUrl }
			registry.add("spring.flyway.user") { postgres.username }
			registry.add("spring.flyway.password") { postgres.password }
			registry.add("app.security.jwt.enabled") { false }
			registry.add("spring.datasource.hikari.maximum-pool-size") { 3 }
		}
	}
}
