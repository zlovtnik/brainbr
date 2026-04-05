DROP POLICY IF EXISTS fiscal_knowledge_base_select ON fiscal_knowledge_base;

CREATE POLICY fiscal_knowledge_base_select ON fiscal_knowledge_base
    FOR SELECT
    USING (
        company_id IS NULL
        OR company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid
    );

DROP POLICY IF EXISTS fiscal_knowledge_base_insert ON fiscal_knowledge_base;
CREATE POLICY fiscal_knowledge_base_insert ON fiscal_knowledge_base
    FOR INSERT
    WITH CHECK (
        company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid
    );

DROP POLICY IF EXISTS fiscal_knowledge_base_update ON fiscal_knowledge_base;
CREATE POLICY fiscal_knowledge_base_update ON fiscal_knowledge_base
    FOR UPDATE
    USING (
        company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid
    )
    WITH CHECK (
        company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid
    );

DROP POLICY IF EXISTS fiscal_knowledge_base_delete ON fiscal_knowledge_base;
CREATE POLICY fiscal_knowledge_base_delete ON fiscal_knowledge_base
    FOR DELETE
    USING (company_id = NULLIF(current_setting('app.current_company_id', true), '')::uuid);
