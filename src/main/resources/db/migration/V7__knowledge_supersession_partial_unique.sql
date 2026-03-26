ALTER TABLE fiscal_knowledge_base
    ADD COLUMN IF NOT EXISTS superseded_at TIMESTAMPTZ;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_indexes WHERE indexname = 'uq_knowledge_law_ref_company'
    ) THEN
        DROP INDEX uq_knowledge_law_ref_company;
    END IF;
END;
$$;

UPDATE fiscal_knowledge_base
SET is_superseded = FALSE
WHERE is_superseded IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_knowledge_law_ref_company_active
    ON fiscal_knowledge_base (law_ref, company_id)
    WHERE is_superseded = FALSE;

-- backfill superseded_at for already-superseded rows without value
UPDATE fiscal_knowledge_base
SET superseded_at = updated_at
WHERE is_superseded = TRUE AND superseded_at IS NULL;
