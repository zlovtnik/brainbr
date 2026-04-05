CREATE OR REPLACE FUNCTION prevent_append_only_table_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Table % is append-only and does not allow % operations', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE IF NOT EXISTS audit_explainability_run (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id        UUID NOT NULL REFERENCES companies(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    sku_id            VARCHAR(50) NOT NULL,
    job_id            VARCHAR(64) NOT NULL,
    request_id        VARCHAR(64),
    artifact_version  VARCHAR(64) NOT NULL,
    schema_version    VARCHAR(32) NOT NULL,
    llm_model_used    VARCHAR(100) NOT NULL,
    vector_id         UUID NOT NULL REFERENCES fiscal_knowledge_chunk(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    audit_confidence  NUMERIC(4,3) NOT NULL CONSTRAINT chk_audit_confidence_range CHECK (audit_confidence >= 0 AND audit_confidence <= 1),
    source_snapshot   JSONB NOT NULL DEFAULT '{}'::jsonb,
    replay_context    JSONB NOT NULL DEFAULT '{}'::jsonb,
    rag_output        JSONB NOT NULL DEFAULT '{}'::jsonb,
    artifact_digest   VARCHAR(64) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_explainability_run_company_sku_created
    ON audit_explainability_run(company_id, sku_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_explainability_run_company_created
    ON audit_explainability_run(company_id, created_at DESC);

CREATE TABLE IF NOT EXISTS split_payment_events (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id            UUID NOT NULL REFERENCES companies(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    sku_id                VARCHAR(50) NOT NULL,
    event_type            VARCHAR(80) NOT NULL,
    amount                NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    currency              CHAR(3) NOT NULL,
    idempotency_key       VARCHAR(128) NOT NULL,
    integration_status    VARCHAR(32) NOT NULL DEFAULT 'queued',
    integration_metadata  JSONB NOT NULL DEFAULT '{}'::jsonb,
    event_payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    request_id            VARCHAR(64),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_split_payment_events_idempotency UNIQUE (company_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_split_payment_events_company_created
    ON split_payment_events(company_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_split_payment_events_company_sku
    ON split_payment_events(company_id, sku_id);

CREATE INDEX IF NOT EXISTS idx_split_payment_events_company_event_type
    ON split_payment_events(company_id, event_type);

ALTER TABLE fiscal_audit_log
    ADD COLUMN IF NOT EXISTS run_id UUID,
    ADD COLUMN IF NOT EXISTS artifact_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS artifact_digest VARCHAR(64);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class r ON c.conrelid = r.oid
        JOIN pg_namespace n ON r.relnamespace = n.oid
        WHERE c.conname = 'fk_fiscal_audit_log_run'
          AND r.relname = 'fiscal_audit_log'
          AND n.nspname = current_schema()
    ) THEN
        ALTER TABLE fiscal_audit_log
            ADD CONSTRAINT fk_fiscal_audit_log_run
                FOREIGN KEY (run_id) REFERENCES audit_explainability_run(id) ON UPDATE CASCADE ON DELETE RESTRICT;
    END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS idx_fiscal_audit_log_run_id
    ON fiscal_audit_log(run_id);

ALTER TABLE audit_explainability_run ENABLE ROW LEVEL SECURITY;
ALTER TABLE split_payment_events ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS audit_explainability_run_admin_bypass ON audit_explainability_run;
DROP POLICY IF EXISTS audit_explainability_run_select ON audit_explainability_run;
DROP POLICY IF EXISTS audit_explainability_run_insert ON audit_explainability_run;
DROP POLICY IF EXISTS audit_explainability_run_update ON audit_explainability_run;
DROP POLICY IF EXISTS audit_explainability_run_delete ON audit_explainability_run;

CREATE POLICY audit_explainability_run_admin_bypass ON audit_explainability_run
    USING (current_setting('app.bypass_rls', true) = 'true')
    WITH CHECK (current_setting('app.bypass_rls', true) = 'true');

CREATE POLICY audit_explainability_run_select ON audit_explainability_run
    FOR SELECT
    USING (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);

CREATE POLICY audit_explainability_run_insert ON audit_explainability_run
    FOR INSERT
    WITH CHECK (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);

DROP POLICY IF EXISTS split_payment_events_admin_bypass ON split_payment_events;
DROP POLICY IF EXISTS split_payment_events_select ON split_payment_events;
DROP POLICY IF EXISTS split_payment_events_insert ON split_payment_events;
DROP POLICY IF EXISTS split_payment_events_update ON split_payment_events;
DROP POLICY IF EXISTS split_payment_events_delete ON split_payment_events;

CREATE POLICY split_payment_events_admin_bypass ON split_payment_events
    USING (current_setting('app.bypass_rls', true) = 'true')
    WITH CHECK (current_setting('app.bypass_rls', true) = 'true');

CREATE POLICY split_payment_events_select ON split_payment_events
    FOR SELECT
    USING (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);

CREATE POLICY split_payment_events_insert ON split_payment_events
    FOR INSERT
    WITH CHECK (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);

DROP POLICY IF EXISTS fiscal_audit_log_update ON fiscal_audit_log;
DROP POLICY IF EXISTS fiscal_audit_log_delete ON fiscal_audit_log;

DROP TRIGGER IF EXISTS trg_fiscal_audit_log_prevent_mutation ON fiscal_audit_log;
CREATE TRIGGER trg_fiscal_audit_log_prevent_mutation
    BEFORE UPDATE OR DELETE ON fiscal_audit_log
    FOR EACH ROW
    EXECUTE FUNCTION prevent_append_only_table_mutation();

DROP TRIGGER IF EXISTS trg_audit_explainability_run_prevent_mutation ON audit_explainability_run;
CREATE TRIGGER trg_audit_explainability_run_prevent_mutation
    BEFORE UPDATE OR DELETE ON audit_explainability_run
    FOR EACH ROW
    EXECUTE FUNCTION prevent_append_only_table_mutation();

-- split_payment_events allows UPDATE only for integration_status/integration_metadata columns.
-- DELETE is always forbidden.
CREATE OR REPLACE FUNCTION prevent_split_payment_events_mutation()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'split_payment_events does not allow DELETE operations';
    END IF;
    -- Allow UPDATE only when restricted to integration_status / integration_metadata
    IF TG_OP = 'UPDATE' THEN
        IF NEW.id <> OLD.id OR NEW.company_id <> OLD.company_id OR NEW.sku_id <> OLD.sku_id
            OR NEW.event_type <> OLD.event_type OR NEW.amount <> OLD.amount
            OR NEW.currency <> OLD.currency OR NEW.idempotency_key <> OLD.idempotency_key
            OR NEW.created_at <> OLD.created_at
            OR NEW.event_payload <> OLD.event_payload
            OR NEW.request_id IS DISTINCT FROM OLD.request_id THEN
            RAISE EXCEPTION 'split_payment_events only allows updating integration_status and integration_metadata';
        END IF;
        RETURN NEW;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_split_payment_events_prevent_mutation ON split_payment_events;
CREATE TRIGGER trg_split_payment_events_prevent_mutation
    BEFORE UPDATE OR DELETE ON split_payment_events
    FOR EACH ROW
    EXECUTE FUNCTION prevent_split_payment_events_mutation();
