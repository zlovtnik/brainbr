package br.fiscalbrain.core.tenant

import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class TenantResolver(private val jdbcTemplate: JdbcTemplate) {
	fun resolve(tenantClaim: String?): UUID? {
		if (tenantClaim.isNullOrBlank()) return null
		// Accept direct UUID claim
		runCatching { UUID.fromString(tenantClaim) }.getOrNull()?.let { return it }

		return jdbcTemplate.query(
			"""
				SELECT id FROM companies WHERE external_tenant_id = ?
			""".trimIndent(),
			{ rs, _ -> rs.getObject("id", UUID::class.java) },
			tenantClaim
		).firstOrNull()
	}
}
