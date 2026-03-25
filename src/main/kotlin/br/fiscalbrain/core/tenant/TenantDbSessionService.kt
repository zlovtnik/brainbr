package br.fiscalbrain.core.tenant

import br.fiscalbrain.core.security.ForbiddenOperationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class TenantDbSessionService(
    private val jdbcTemplate: JdbcTemplate
) {
    fun requireCompanyId(): UUID {
        return TenantContextHolder.get()
            ?: throw ForbiddenOperationException("Tenant context is not available")
    }

    @Transactional
    fun apply(companyId: UUID) {
        jdbcTemplate.queryForObject(
            """
            SELECT set_config('app.current_company_id', ?, TRUE),
                   set_config('app.bypass_rls', 'false', TRUE)
            """.trimIndent(),
            String::class.java,
            companyId.toString()
        )
    }

    @Transactional
    fun applyAndRun(companyId: UUID, block: () -> Unit) {
        TenantContextHolder.withTenant(companyId) {
            apply(companyId)
            block()
        }
    }

    @Transactional
    fun <T> applyAndRunWithResult(companyId: UUID, block: () -> T): T {
        return TenantContextHolder.withTenant(companyId) {
            apply(companyId)
            block()
        }
    }
}
