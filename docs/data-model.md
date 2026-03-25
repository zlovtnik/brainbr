# Data Model

## Mandatory extensions

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

## Required core tables

- `companies`
- `fiscal_knowledge_base`
- `inventory_transition`
- `transition_calendar`
- `fiscal_audit_log`
- `split_payment_events` (Phase 3-ready)

## Key constraints and indexes

- `fiscal_knowledge_base`: unique `(law_ref, company_id)`.
- `inventory_transition`: primary key `(sku_id, company_id)`.
- `inventory_transition`: indexes on `(company_id)`, `(ncm_code)`, `(transition_risk_score)`.
- `fiscal_audit_log`: indexes on `(company_id, sku_id)`, `(event_type)`, `(created_at desc)`.
- Vector ANN: HNSW on `fiscal_knowledge_base.embedding`.

## Tenant policy standard

```sql
ALTER TABLE inventory_transition ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_select_modify ON inventory_transition
USING (company_id = current_setting('app.current_company_id', true)::uuid)
WITH CHECK (company_id = current_setting('app.current_company_id', true)::uuid);
```

Apply equivalent policy to all tenant-owned tables.

## JSON contracts

Owning columns and nullability/defaults:

- `inventory_transition.legacy_taxes` (`jsonb NOT NULL DEFAULT '{}'::jsonb`)
  - Required keys: `icms`, `pis`, `cofins`, `iss`, optional: `st`, `st_mva`, `ipi`, `fcp`.
- `inventory_transition.reform_taxes` (`jsonb NOT NULL DEFAULT '{}'::jsonb`)
  - Required keys: `ibs`, `cbs`, `tax_rate`, `is_taxable`, optional: `cashback_eligible`, `regime`, `reduced_basket`, `confidence`.

Contract references:

- `docs/schemas/legacy-taxes-v1.schema.json`
- `docs/schemas/reform-taxes-v1.schema.json`
- `docs/schemas/rag-output-v1.schema.json`

Enforcement strategy:

- Application-level validation is mandatory before persistence using:
  - `validateLegacyTaxes(payload)` against `legacy-taxes-v1.schema.json`
  - `validateReformTaxes(payload)` against `reform-taxes-v1.schema.json`
  - `validateRagOutput(payload)` against `rag-output-v1.schema.json`
- DB-level checks are recommended for defense in depth and should use constraint names:
  - `chk_inventory_transition_legacy_taxes`
  - `chk_inventory_transition_reform_taxes`

## Migration rules

- Every schema change must be in Flyway migration scripts.
- No direct production `ALTER TABLE` operations outside migrations.
