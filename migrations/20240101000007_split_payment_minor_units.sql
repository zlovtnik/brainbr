ALTER TABLE split_payment_events
    ADD COLUMN IF NOT EXISTS event_timestamp TIMESTAMPTZ;

UPDATE split_payment_events
SET event_timestamp = COALESCE(event_timestamp, created_at)
WHERE event_timestamp IS NULL;

ALTER TABLE split_payment_events
    ALTER COLUMN event_timestamp SET NOT NULL,
    ALTER COLUMN event_timestamp SET DEFAULT NOW();

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'split_payment_events'
          AND column_name = 'amount'
          AND data_type NOT IN ('bigint', 'integer')
    ) THEN
        ALTER TABLE split_payment_events
            ALTER COLUMN amount TYPE BIGINT USING ROUND(amount * 100)::BIGINT;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS split_payment_event_statuses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID NOT NULL REFERENCES split_payment_events(id) ON UPDATE CASCADE ON DELETE CASCADE,
    company_id      UUID NOT NULL REFERENCES companies(id) ON UPDATE CASCADE ON DELETE RESTRICT,
    status          VARCHAR(32) NOT NULL,
    status_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    request_id      VARCHAR(64),
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_split_payment_event_statuses_company_event_changed
    ON split_payment_event_statuses(company_id, event_id, changed_at DESC);

CREATE INDEX IF NOT EXISTS idx_split_payment_event_statuses_company_changed
    ON split_payment_event_statuses(company_id, changed_at DESC);

INSERT INTO split_payment_event_statuses (
    event_id, company_id, status, status_metadata, request_id, changed_at
)
SELECT e.id,
       e.company_id,
       e.integration_status,
       COALESCE(e.integration_metadata, '{}'::jsonb),
       e.request_id,
       e.created_at
FROM split_payment_events e
WHERE NOT EXISTS (
    SELECT 1
    FROM split_payment_event_statuses s
    WHERE s.event_id = e.id
)
AND e.integration_status IS NOT NULL;

ALTER TABLE split_payment_event_statuses ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS split_payment_event_statuses_admin_bypass ON split_payment_event_statuses;
DROP POLICY IF EXISTS split_payment_event_statuses_select ON split_payment_event_statuses;
DROP POLICY IF EXISTS split_payment_event_statuses_insert ON split_payment_event_statuses;
DROP POLICY IF EXISTS split_payment_event_statuses_update ON split_payment_event_statuses;
DROP POLICY IF EXISTS split_payment_event_statuses_delete ON split_payment_event_statuses;

CREATE POLICY split_payment_event_statuses_admin_bypass ON split_payment_event_statuses
    USING (current_setting('app.bypass_rls', true) = 'true')
    WITH CHECK (current_setting('app.bypass_rls', true) = 'true');

CREATE POLICY split_payment_event_statuses_select ON split_payment_event_statuses
    FOR SELECT
    USING (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);

CREATE POLICY split_payment_event_statuses_insert ON split_payment_event_statuses
    FOR INSERT
    WITH CHECK (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);

DROP TRIGGER IF EXISTS trg_split_payment_event_statuses_prevent_mutation ON split_payment_event_statuses;
CREATE TRIGGER trg_split_payment_event_statuses_prevent_mutation
    BEFORE UPDATE OR DELETE ON split_payment_event_statuses
    FOR EACH ROW
    EXECUTE FUNCTION prevent_append_only_table_mutation();
