CREATE TABLE IF NOT EXISTS companies (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(255) NOT NULL,
    cnpj         VARCHAR(18) UNIQUE NOT NULL,
    plan         VARCHAR(50) NOT NULL DEFAULT 'starter',
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS fiscal_knowledge_base (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id     UUID,
    law_ref        VARCHAR(255) NOT NULL,
    law_type       VARCHAR(50) NOT NULL,
    content        TEXT NOT NULL,
    embedding      VECTOR(1536),
    metadata       JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_url     TEXT,
    published_at   DATE,
    effective_at   DATE,
    is_superseded  BOOLEAN NOT NULL DEFAULT FALSE,
    superseded_by  UUID REFERENCES fiscal_knowledge_base(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_fkb_company FOREIGN KEY (company_id) REFERENCES companies(id) ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_knowledge_law_ref_company
    ON fiscal_knowledge_base (law_ref, company_id);

CREATE INDEX IF NOT EXISTS idx_knowledge_company
    ON fiscal_knowledge_base (company_id);

CREATE INDEX IF NOT EXISTS idx_knowledge_metadata
    ON fiscal_knowledge_base USING gin(metadata);

CREATE TABLE IF NOT EXISTS inventory_transition (
    sku_id             VARCHAR(50) NOT NULL,
    company_id         UUID NOT NULL,
    description        TEXT NOT NULL,
    ncm_code           VARCHAR(10) NOT NULL,
    cest_code          VARCHAR(9),
    origin_state       CHAR(2) NOT NULL,
    destination_state  CHAR(2) NOT NULL,
    unit_of_measure    VARCHAR(10) NOT NULL DEFAULT 'UN',
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    legacy_taxes       JSONB NOT NULL DEFAULT '{}'::jsonb,
    reform_taxes       JSONB NOT NULL DEFAULT '{}'::jsonb,
    transition_risk_score SMALLINT CHECK (transition_risk_score BETWEEN 1 AND 10),
    last_llm_audit     TIMESTAMPTZ,
    llm_model_used     VARCHAR(100),
    vector_id          UUID REFERENCES fiscal_knowledge_base(id),
    audit_confidence   NUMERIC(4,3),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (sku_id, company_id),
    CONSTRAINT fk_inventory_transition_company FOREIGN KEY (company_id) REFERENCES companies(id) ON UPDATE CASCADE ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_inv_company ON inventory_transition (company_id);
CREATE INDEX IF NOT EXISTS idx_inv_ncm ON inventory_transition (ncm_code);
CREATE INDEX IF NOT EXISTS idx_inv_risk ON inventory_transition (transition_risk_score);

ALTER TABLE fiscal_knowledge_base ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS fiscal_knowledge_base_admin_bypass ON fiscal_knowledge_base;
DROP POLICY IF EXISTS fiscal_knowledge_base_select ON fiscal_knowledge_base;
DROP POLICY IF EXISTS fiscal_knowledge_base_insert ON fiscal_knowledge_base;
DROP POLICY IF EXISTS fiscal_knowledge_base_update ON fiscal_knowledge_base;
DROP POLICY IF EXISTS fiscal_knowledge_base_delete ON fiscal_knowledge_base;

CREATE POLICY fiscal_knowledge_base_admin_bypass ON fiscal_knowledge_base
    USING (current_setting('app.bypass_rls', true) = 'true')
    WITH CHECK (current_setting('app.bypass_rls', true) = 'true');

CREATE POLICY fiscal_knowledge_base_select ON fiscal_knowledge_base
    FOR SELECT
    USING (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);

CREATE POLICY fiscal_knowledge_base_insert ON fiscal_knowledge_base
    FOR INSERT
    WITH CHECK (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);

CREATE POLICY fiscal_knowledge_base_update ON fiscal_knowledge_base
    FOR UPDATE
    USING (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid)
    WITH CHECK (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);

CREATE POLICY fiscal_knowledge_base_delete ON fiscal_knowledge_base
    FOR DELETE
    USING (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);

ALTER TABLE inventory_transition ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS inventory_transition_admin_bypass ON inventory_transition;
DROP POLICY IF EXISTS inventory_transition_select ON inventory_transition;
DROP POLICY IF EXISTS inventory_transition_insert ON inventory_transition;
DROP POLICY IF EXISTS inventory_transition_update ON inventory_transition;
DROP POLICY IF EXISTS inventory_transition_delete ON inventory_transition;

CREATE POLICY inventory_transition_admin_bypass ON inventory_transition
    USING (current_setting('app.bypass_rls', true) = 'true')
    WITH CHECK (current_setting('app.bypass_rls', true) = 'true');

CREATE POLICY inventory_transition_select ON inventory_transition
    FOR SELECT
    USING (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);

CREATE POLICY inventory_transition_insert ON inventory_transition
    FOR INSERT
    WITH CHECK (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);

CREATE POLICY inventory_transition_update ON inventory_transition
    FOR UPDATE
    USING (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid)
    WITH CHECK (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);

CREATE POLICY inventory_transition_delete ON inventory_transition
    FOR DELETE
    USING (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);

CREATE TABLE IF NOT EXISTS transition_calendar (
    id              SERIAL PRIMARY KEY,
    year            SMALLINT NOT NULL UNIQUE,
    ibs_pct         NUMERIC(5,2) NOT NULL,
    cbs_pct         NUMERIC(5,2) NOT NULL,
    icms_factor     NUMERIC(4,3) NOT NULL,
    iss_factor      NUMERIC(4,3) NOT NULL,
    pis_active      BOOLEAN NOT NULL,
    cofins_active   BOOLEAN NOT NULL,
    notes           TEXT,
    law_ref         VARCHAR(255)
);

INSERT INTO transition_calendar (year, ibs_pct, cbs_pct, icms_factor, iss_factor, pis_active, cofins_active, notes, law_ref)
VALUES
    (2026, 0.10, 0.90, 1.000, 1.000, TRUE, TRUE, 'Shadow year - IBS/CBS test', 'LC 68/2024'),
    (2027, 0.00, 8.80, 1.000, 1.000, FALSE, FALSE, 'CBS full; PIS/COFINS extinct', 'LC 68/2024'),
    (2028, 0.00, 8.80, 1.000, 1.000, FALSE, FALSE, 'Same as 2027', 'LC 68/2024'),
    (2029, 1.75, 8.80, 0.900, 0.900, FALSE, FALSE, 'ICMS/ISS -10%', 'LC 68/2024'),
    (2030, 3.50, 8.80, 0.800, 0.800, FALSE, FALSE, 'ICMS/ISS -20%', 'LC 68/2024'),
    (2031, 5.25, 8.80, 0.700, 0.700, FALSE, FALSE, 'ICMS/ISS -30%', 'LC 68/2024'),
    (2032, 7.00, 8.80, 0.600, 0.600, FALSE, FALSE, 'ICMS/ISS -40%', 'LC 68/2024'),
    (2033, 17.50, 8.80, 0.000, 0.000, FALSE, FALSE, 'Reform complete', 'LC 68/2024')
ON CONFLICT (year) DO NOTHING;
