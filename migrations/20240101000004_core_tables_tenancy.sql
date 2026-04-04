CREATE TABLE companies (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	external_tenant_id TEXT UNIQUE NOT NULL,
	name TEXT NOT NULL,
	created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fiscal_knowledge_base (
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

CREATE TABLE inventory_transition (
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

CREATE TABLE transition_calendar (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	company_id uuid NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
	year INT NOT NULL,
	ibs_rate NUMERIC,
	cbs_rate NUMERIC,
	notes TEXT,
	created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	CONSTRAINT uq_transition_calendar_company_year UNIQUE(company_id, year)
);

CREATE TABLE fiscal_audit_log (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	company_id uuid NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
	sku_id TEXT,
	event_type TEXT,
	payload jsonb,
	vector_id uuid,
	law_ref TEXT,
	created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE split_payment_events (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	company_id uuid NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
	event_type TEXT,
	payload jsonb,
	created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes
CREATE INDEX idx_fiscal_knowledge_base_company_law_ref ON fiscal_knowledge_base(company_id, law_ref);
CREATE INDEX idx_fiscal_knowledge_base_embedding ON fiscal_knowledge_base USING hnsw (embedding vector_cosine_ops);

CREATE INDEX idx_inventory_transition_company ON inventory_transition(company_id);
CREATE INDEX idx_inventory_transition_ncm ON inventory_transition(ncm_code);
CREATE INDEX idx_inventory_transition_risk ON inventory_transition(transition_risk_score);

CREATE INDEX idx_transition_calendar_company_year ON transition_calendar(company_id, year);

CREATE INDEX idx_fiscal_audit_log_company_sku ON fiscal_audit_log(company_id, sku_id);
CREATE INDEX idx_fiscal_audit_log_event_type ON fiscal_audit_log(event_type);
CREATE INDEX idx_fiscal_audit_log_created_desc ON fiscal_audit_log(company_id, created_at DESC);

CREATE INDEX idx_split_payment_events_company_created ON split_payment_events(company_id, created_at DESC);

-- RLS policies
ALTER TABLE fiscal_knowledge_base ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory_transition ENABLE ROW LEVEL SECURITY;
ALTER TABLE transition_calendar ENABLE ROW LEVEL SECURITY;
ALTER TABLE fiscal_audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE split_payment_events ENABLE ROW LEVEL SECURITY;

-- NOTE: app.bypass_rls is a privileged escape hatch to temporarily bypass tenant RLS.
--   * It must only be set by trusted server-side code paths (never user input).
--   * Scope it to the shortest possible transaction and reset immediately after use.
--   * All uses should be audited (server-side logging or DB audit triggers).
--   * Tenant context must always be derived from app.current_company_id on the server.
DO $$
DECLARE
	policy_tables TEXT[] := ARRAY[
		'fiscal_knowledge_base',
		'inventory_transition',
		'transition_calendar',
		'fiscal_audit_log',
		'split_payment_events'
	];
	_table TEXT;
BEGIN
	FOREACH _table IN ARRAY policy_tables LOOP
		EXECUTE format(
			'CREATE POLICY %I_tenant_policy ON %I USING (CASE WHEN current_setting(''app.bypass_rls'', true) = ''true'' THEN true ELSE NULLIF(current_setting(''app.current_company_id'', true), '''') IS NOT NULL AND company_id = NULLIF(current_setting(''app.current_company_id'', true), '''')::uuid END) WITH CHECK (CASE WHEN current_setting(''app.bypass_rls'', true) = ''true'' THEN true ELSE NULLIF(current_setting(''app.current_company_id'', true), '''') IS NOT NULL AND company_id = NULLIF(current_setting(''app.current_company_id'', true), '''')::uuid END);',
			_table,
			_table
		);
	END LOOP;
END $$;
