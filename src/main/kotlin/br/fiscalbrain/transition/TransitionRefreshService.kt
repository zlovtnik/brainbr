package br.fiscalbrain.transition

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
@Profile("worker")
class TransitionRefreshService(
    private val jdbcTemplate: JdbcTemplate
) {
    private val logger = LoggerFactory.getLogger(TransitionRefreshService::class.java)

    fun refreshMaterializedView() {
        val lockAcquired = jdbcTemplate.queryForObject("SELECT pg_try_advisory_lock(?)", Boolean::class.java, LOCK_KEY) ?: false
        if (!lockAcquired) {
            logger.debug("Skipping mv_fiscal_impact refresh; another worker holds the lock")
            return
        }

        val start = Instant.now()
        try {
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_fiscal_impact")
            backfillMissingRiskScores()
            val duration = Duration.between(start, Instant.now()).toMillis()
            logger.info("mv_fiscal_impact refresh complete in {} ms", duration)
        } finally {
            val unlocked = jdbcTemplate.queryForObject("SELECT pg_advisory_unlock(?)", Boolean::class.java, LOCK_KEY) ?: false
            if (!unlocked) {
                logger.warn("Failed to release advisory lock {}", LOCK_KEY)
            }
        }
    }

    private fun backfillMissingRiskScores() {
        val updated = jdbcTemplate.update(
            """
                WITH calc AS (
                    SELECT sku_id,
                           company_id,
                           COALESCE((SELECT SUM((value)::numeric) FROM jsonb_each_text(legacy_taxes)), 0) AS legacy_total,
                           COALESCE((reform_taxes ->> 'tax_rate')::numeric, 0) AS reform_total,
                           COALESCE(audit_confidence, 0.5) AS audit_confidence
                    FROM inventory_transition
                    WHERE is_active = TRUE
                      AND transition_risk_score IS NULL
                )
                UPDATE inventory_transition i
                SET transition_risk_score = LEAST(10, GREATEST(1, CEIL((0.6 * LEAST(ABS(calc.reform_total - calc.legacy_total) / 30.0, 1.0)
                    + 0.4 * (1 - calc.audit_confidence)) * 10)::int)),
                    updated_at = NOW()
                FROM calc
                WHERE i.sku_id = calc.sku_id
                  AND i.company_id = calc.company_id
                  AND i.is_active = TRUE
            """.trimIndent()
        )

        if (updated > 0) {
            logger.info("Backfilled transition_risk_score for {} records", updated)
        }
    }

    companion object {
        private const val LOCK_KEY: Long = 9988776655L
    }
}
