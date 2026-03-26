CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

INSERT INTO companies (id, external_tenant_id, name)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'tenant-a', 'Tenant A'),
    ('00000000-0000-0000-0000-000000000002', 'tenant-b', 'Tenant B')
ON CONFLICT DO NOTHING;

INSERT INTO inventory_transition (company_id, sku_id, description, ncm_code, legacy_taxes, reform_taxes)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'SKU-1', 'Sample SKU A', '22030000', '{}'::jsonb, '{}'::jsonb)
ON CONFLICT (company_id, sku_id) DO NOTHING;
