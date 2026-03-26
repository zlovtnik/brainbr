package br.fiscalbrain.transition

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
class TransitionCalendarRepository(
    private val jdbcTemplate: JdbcTemplate
) {
    private val mapper: RowMapper<TransitionWeights> = RowMapper { rs, _ ->
        TransitionWeights(
            year = rs.getInt("year"),
            reformWeight = rs.getDouble("reform_weight"),
            legacyWeight = rs.getDouble("legacy_weight")
        )
    }

    fun list(): List<TransitionWeights> =
        jdbcTemplate.query(
            "$CALENDAR_SQL_BASE ORDER BY year",
            mapper
        )

    fun find(year: Int): TransitionWeights? =
        jdbcTemplate.query(
            "$CALENDAR_SQL_BASE WHERE year = ?",
            mapper,
            year
        ).firstOrNull()

    companion object {
        private const val CALENDAR_SQL_BASE = """
            SELECT
                year,
                CASE WHEN year = 2026 THEN 0.10 ELSE 1.0 END AS reform_weight,
                CASE WHEN year = 2026 THEN 0.90 ELSE 0.0 END AS legacy_weight
            FROM transition_calendar
        """
    }
}
