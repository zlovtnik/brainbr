DROP POLICY IF EXISTS fiscal_knowledge_base_select ON fiscal_knowledge_base;

CREATE POLICY fiscal_knowledge_base_select ON fiscal_knowledge_base
    FOR SELECT
    USING (
        company_id IS NULL
        OR company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid
    );
