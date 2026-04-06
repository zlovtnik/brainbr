CREATE TABLE IF NOT EXISTS companies (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	external_tenant_id TEXT UNIQUE NOT NULL,
	name TEXT NOT NULL,
	created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS fiscal_knowledge_base (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	company_id uuid NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
	law_ref TEXT NOT NULL,
	title TEXT,
	content TEXT,
	embedding vector(1536),
	source_url TEXT,
	metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
	effective_at DATE,
	created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	UNIQUE (law_ref, company_id)
);

CREATE TABLE IF NOT EXISTS inventory_transition (
	company_id uuid NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
	sku_id TEXT NOT NULL,
	description TEXT,
	ncm_code TEXT,
	origin_state CHAR(2),
	destination_state CHAR(2),
	legacy_taxes jsonb NOT NULL DEFAULT '{}'::jsonb,
	reform_taxes jsonb NOT NULL DEFAULT '{}'::jsonb,
	transition_risk_score INT DEFAULT 0,
	audit_confidence NUMERIC,
	llm_model_used TEXT,
	vector_id uuid,
	law_ref TEXT,
	is_active BOOLEAN NOT NULL DEFAULT TRUE,
	created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	PRIMARY KEY (sku_id, company_id)
);

-- transition_calendar is fully defined in 000003 (global lookup table, no company_id).

-- fiscal_audit_log and split_payment_events are fully defined in 000005; nothing to create here.

-- Indexes
CREATE INDEX IF NOT EXISTS idx_fiscal_knowledge_base_company_law_ref ON fiscal_knowledge_base(company_id, law_ref);
CREATE INDEX IF NOT EXISTS idx_fiscal_knowledge_base_embedding ON fiscal_knowledge_base USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS idx_inventory_transition_company ON inventory_transition(company_id);
CREATE INDEX IF NOT EXISTS idx_inventory_transition_ncm ON inventory_transition(ncm_code);
CREATE INDEX IF NOT EXISTS idx_inventory_transition_risk ON inventory_transition(transition_risk_score);

-- transition_calendar is a global lookup table (no company_id); index is on year only (already unique in 000003).

-- Indexes for fiscal_audit_log and split_payment_events are in 000005.

-- RLS policies
ALTER TABLE fiscal_knowledge_base ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory_transition ENABLE ROW LEVEL SECURITY;

-- NOTE: app.bypass_rls is a privileged escape hatch to temporarily bypass tenant RLS.
--   * It must only be set by trusted server-side code paths (never user input).
--   * Scope it to the shortest possible transaction and reset immediately after use.
--   * All uses should be audited (server-side logging or DB audit triggers).
--   * Tenant context must always be derived from app.current_company_id on the server.
DO $$
DECLARE
	policy_tables TEXT[] := ARRAY[
		'fiscal_knowledge_base',
		'inventory_transition'
	];
	_table TEXT;
BEGIN
	FOREACH _table IN ARRAY policy_tables LOOP
		EXECUTE format('DROP POLICY IF EXISTS %I_tenant_policy ON %I;', _table, _table);
		EXECUTE format(
			'CREATE POLICY %I_tenant_policy ON %I USING (CASE WHEN current_setting(''app.bypass_rls'', true) = ''true'' THEN true ELSE NULLIF(current_setting(''app.current_company_id'', true), '''') IS NOT NULL AND company_id = NULLIF(current_setting(''app.current_company_id'', true), '''')::uuid END) WITH CHECK (CASE WHEN current_setting(''app.bypass_rls'', true) = ''true'' THEN true ELSE NULLIF(current_setting(''app.current_company_id'', true), '''') IS NOT NULL AND company_id = NULLIF(current_setting(''app.current_company_id'', true), '''')::uuid END);',
			_table,
			_table
		);
	END LOOP;
END $$;
