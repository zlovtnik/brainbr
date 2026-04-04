ALTER TABLE fiscal_knowledge_base
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS content_version INTEGER NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS fiscal_knowledge_chunk (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    knowledge_id  UUID NOT NULL REFERENCES fiscal_knowledge_base(id) ON DELETE CASCADE,
    company_id    UUID NOT NULL REFERENCES companies(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    chunk_index   INTEGER NOT NULL,
    content       TEXT NOT NULL,
    embedding     VECTOR(1536),
    metadata      JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_knowledge_chunk_idx UNIQUE (knowledge_id, chunk_index)
);

CREATE OR REPLACE FUNCTION fiscal_update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_fiscal_knowledge_chunk_updated_at ON fiscal_knowledge_chunk;

CREATE TRIGGER trg_fiscal_knowledge_chunk_updated_at
    BEFORE UPDATE ON fiscal_knowledge_chunk
    FOR EACH ROW
    EXECUTE FUNCTION fiscal_update_updated_at_column();

CREATE INDEX IF NOT EXISTS idx_fiscal_knowledge_chunk_company
    ON fiscal_knowledge_chunk(company_id);

CREATE INDEX IF NOT EXISTS idx_fiscal_knowledge_chunk_knowledge
    ON fiscal_knowledge_chunk(knowledge_id);

CREATE INDEX IF NOT EXISTS idx_fiscal_knowledge_chunk_metadata
    ON fiscal_knowledge_chunk USING gin(metadata);

CREATE TABLE IF NOT EXISTS fiscal_audit_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id    UUID NOT NULL REFERENCES companies(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    sku_id        VARCHAR(50) NOT NULL,
    event_type    VARCHAR(80) NOT NULL,
    actor         VARCHAR(255) NOT NULL DEFAULT 'system',
    request_id    VARCHAR(64),
    event_payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fiscal_audit_log_company_sku
    ON fiscal_audit_log(company_id, sku_id);

CREATE INDEX IF NOT EXISTS idx_fiscal_audit_log_event_type
    ON fiscal_audit_log(event_type);

CREATE INDEX IF NOT EXISTS idx_fiscal_audit_log_created_at
    ON fiscal_audit_log(created_at DESC);

ALTER TABLE inventory_transition
    DROP CONSTRAINT IF EXISTS inventory_transition_vector_id_fkey;

ALTER TABLE inventory_transition
    ADD CONSTRAINT inventory_transition_vector_id_fkey
    FOREIGN KEY (vector_id) REFERENCES fiscal_knowledge_chunk(id) ON DELETE SET NULL;

ALTER TABLE fiscal_knowledge_chunk ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS fiscal_knowledge_chunk_admin_bypass ON fiscal_knowledge_chunk;
DROP POLICY IF EXISTS fiscal_knowledge_chunk_select ON fiscal_knowledge_chunk;
DROP POLICY IF EXISTS fiscal_knowledge_chunk_insert ON fiscal_knowledge_chunk;
DROP POLICY IF EXISTS fiscal_knowledge_chunk_update ON fiscal_knowledge_chunk;
DROP POLICY IF EXISTS fiscal_knowledge_chunk_delete ON fiscal_knowledge_chunk;

CREATE POLICY fiscal_knowledge_chunk_admin_bypass ON fiscal_knowledge_chunk
    USING (current_setting('app.bypass_rls', true) = 'true')
    WITH CHECK (current_setting('app.bypass_rls', true) = 'true');

CREATE POLICY fiscal_knowledge_chunk_select ON fiscal_knowledge_chunk
    FOR SELECT
    USING (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);

CREATE POLICY fiscal_knowledge_chunk_insert ON fiscal_knowledge_chunk
    FOR INSERT
    WITH CHECK (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);

CREATE POLICY fiscal_knowledge_chunk_update ON fiscal_knowledge_chunk
    FOR UPDATE
    USING (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid)
    WITH CHECK (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);

CREATE POLICY fiscal_knowledge_chunk_delete ON fiscal_knowledge_chunk
    FOR DELETE
    USING (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);

ALTER TABLE fiscal_audit_log ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS fiscal_audit_log_admin_bypass ON fiscal_audit_log;
DROP POLICY IF EXISTS fiscal_audit_log_select ON fiscal_audit_log;
DROP POLICY IF EXISTS fiscal_audit_log_insert ON fiscal_audit_log;
DROP POLICY IF EXISTS fiscal_audit_log_update ON fiscal_audit_log;
DROP POLICY IF EXISTS fiscal_audit_log_delete ON fiscal_audit_log;

CREATE POLICY fiscal_audit_log_admin_bypass ON fiscal_audit_log
    USING (current_setting('app.bypass_rls', true) = 'true')
    WITH CHECK (current_setting('app.bypass_rls', true) = 'true');

CREATE POLICY fiscal_audit_log_select ON fiscal_audit_log
    FOR SELECT
    USING (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);

CREATE POLICY fiscal_audit_log_insert ON fiscal_audit_log
    FOR INSERT
    WITH CHECK (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);

-- UPDATE and DELETE intentionally omitted — audit log is append-only (enforced by trigger below).
DROP POLICY IF EXISTS fiscal_audit_log_update ON fiscal_audit_log;
DROP POLICY IF EXISTS fiscal_audit_log_delete ON fiscal_audit_log;

CREATE OR REPLACE FUNCTION fiscal_audit_log_prevent_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'fiscal_audit_log is append-only and does not allow % operations', TG_OP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_fiscal_audit_log_immutable ON fiscal_audit_log;
CREATE TRIGGER trg_fiscal_audit_log_immutable
    BEFORE UPDATE OR DELETE ON fiscal_audit_log
    FOR EACH ROW
    EXECUTE FUNCTION fiscal_audit_log_prevent_mutation();
