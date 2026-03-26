-- Transition weights view for reform vs legacy blending
CREATE OR REPLACE VIEW transition_calendar_weights AS
SELECT
    year,
    CASE WHEN year = 2026 THEN 0.10 ELSE 1.0 END AS reform_weight,
    CASE WHEN year = 2026 THEN 0.90 ELSE 0.0 END AS legacy_weight
FROM transition_calendar;

CREATE INDEX IF NOT EXISTS idx_transition_calendar_year ON transition_calendar (year);

-- Materialized view for fiscal impact reporting
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_fiscal_impact AS
WITH calc AS (
    SELECT
        i.sku_id,
        i.company_id,
        i.ncm_code,
        COALESCE((SELECT SUM((value)::numeric)
                  FROM jsonb_each_text(i.legacy_taxes)
                  WHERE value ~ '^-?[0-9]+\\.?[0-9]*$'), 0) AS legacy_burden,
        COALESCE((SELECT SUM((value)::numeric)
                  FROM jsonb_each_text(i.reform_taxes)
                  WHERE value ~ '^-?[0-9]+\\.?[0-9]*$'), 0) AS reform_burden,
        i.transition_risk_score,
        i.audit_confidence,
        i.llm_model_used,
        i.updated_at
    FROM inventory_transition i
    WHERE i.is_active = TRUE
)
SELECT
    sku_id,
    company_id,
    ncm_code,
    legacy_burden,
    reform_burden,
    reform_burden - legacy_burden AS delta,
    transition_risk_score,
    audit_confidence,
    llm_model_used,
    updated_at
FROM calc;

CREATE UNIQUE INDEX IF NOT EXISTS ux_mv_fiscal_impact_company_sku ON mv_fiscal_impact (company_id, sku_id);
CREATE INDEX IF NOT EXISTS idx_mv_fiscal_impact_company ON mv_fiscal_impact (company_id);
CREATE INDEX IF NOT EXISTS idx_mv_fiscal_impact_risk ON mv_fiscal_impact (transition_risk_score);
CREATE INDEX IF NOT EXISTS idx_mv_fiscal_impact_updated ON mv_fiscal_impact (updated_at);

-- Supporting index to speed up risk lookups
CREATE INDEX IF NOT EXISTS idx_inventory_transition_updated_risk ON inventory_transition (updated_at, transition_risk_score);
