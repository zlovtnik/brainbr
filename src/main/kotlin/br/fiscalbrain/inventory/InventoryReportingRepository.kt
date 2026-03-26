package br.fiscalbrain.inventory

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

@Repository
class InventoryReportingRepository(
    private val jdbcTemplate: JdbcTemplate
) {

    private val impactMapper = RowMapper { rs: ResultSet, _: Int ->
        ImpactItem(
            skuId = rs.getString("sku_id"),
            ncmCode = rs.getString("ncm_code"),
            legacyBurden = rs.getDouble("legacy_burden"),
            reformBurden = rs.getDouble("reform_burden"),
            delta = rs.getDouble("delta"),
            transitionRiskScore = rs.getInt("transition_risk_score").let { if (rs.wasNull()) null else it },
            updatedAt = requireInstant(rs, "updated_at")
        )
    }

    private val riskMapper = RowMapper { rs: ResultSet, _: Int ->
        val riskScore = rs.getInt("transition_risk_score").let { if (rs.wasNull()) null else it }
        val auditConfidenceRaw = rs.getDouble("audit_confidence")
        val auditConfidence = if (rs.wasNull()) null else auditConfidenceRaw

        val metadata = mutableMapOf<String, Any>(
            "ncm_code" to (rs.getString("ncm_code") ?: ""),
            "llm_model_used" to (rs.getString("llm_model_used") ?: ""),
            "legacy_burden" to rs.getDouble("legacy_burden"),
            "reform_burden" to rs.getDouble("reform_burden")
        )
        if (auditConfidence != null) {
            metadata["audit_confidence"] = auditConfidence
        }
        RiskItem(
            skuId = rs.getString("sku_id"),
            transitionRiskScore = riskScore,
            lastUpdated = requireInstant(rs, "updated_at"),
            metadata = metadata
        )
    }

    fun listImpact(companyId: UUID, query: PageQuery): PagedResult<ImpactItem> {
        require(query.page >= 1) { "Page must be >= 1" }
        val offset = (query.page - 1) * query.limit
        val items = jdbcTemplate.query(
            """
                SELECT sku_id, company_id, ncm_code, legacy_burden, reform_burden, delta, transition_risk_score, updated_at
                FROM mv_fiscal_impact
                WHERE company_id = ?
                ORDER BY updated_at DESC
                LIMIT ? OFFSET ?
            """.trimIndent(),
            impactMapper,
            companyId,
            query.limit,
            offset
        )

        val total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_fiscal_impact WHERE company_id = ?",
            Long::class.java,
            companyId
        ) ?: 0L

        return PagedResult(
            items = items,
            totalCount = total
        )
    }

    fun listRisk(companyId: UUID, query: RiskQuery): PagedResult<RiskItem> {
        require(query.page >= 1) { "Page must be >= 1" }
        val offset = (query.page - 1) * query.limit
        val params = mutableListOf<Any>(companyId)
        val filters = StringBuilder("company_id = ?")

        query.minTransitionRiskScore?.let {
            filters.append(" AND transition_risk_score >= ?")
            params.add(it)
        }
        query.maxTransitionRiskScore?.let {
            filters.append(" AND transition_risk_score <= ?")
            params.add(it)
        }
        query.ncmCode?.let {
            val escaped = it
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            filters.append(" AND ncm_code ILIKE ? ESCAPE '\\\\'")
            params.add("%$escaped%")
        }
        query.updatedAfter?.let {
            filters.append(" AND updated_at > ?")
            params.add(it)
        }

        val sortDir = when (query.sortDir?.lowercase()) {
            "asc" -> "ASC"
            else -> "DESC"
        }

        val sql = """
            SELECT sku_id, ncm_code, transition_risk_score, updated_at, audit_confidence, llm_model_used, legacy_burden, reform_burden
            FROM mv_fiscal_impact
            WHERE $filters
            ORDER BY transition_risk_score $sortDir NULLS LAST, updated_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()

        params.add(query.limit)
        params.add(offset)

        val items = jdbcTemplate.query(sql, riskMapper, *params.toTypedArray())

        val countSql = "SELECT COUNT(*) FROM mv_fiscal_impact WHERE $filters"
        val total = jdbcTemplate.queryForObject(countSql, Long::class.java, *params.dropLast(2).toTypedArray()) ?: 0L

        return PagedResult(
            items = items,
            totalCount = total
        )
    }

    private fun requireInstant(rs: ResultSet, column: String): Instant {
        val ts = rs.getTimestamp(column)
            ?: throw SQLException("Column $column is null")
        return ts.toInstant()
    }
}

data class PagedResult<T>(
    val items: List<T>,
    val totalCount: Long
)
