# SQL Notes

## Migration policy

- All schema changes must ship via Flyway migrations in `src/main/resources/db/migration`.
- No direct production `ALTER TABLE` outside migration scripts.

## Tenancy model and RLS

- Tenant-owned tables use `company_id` and RLS policies with `USING` and `WITH CHECK`.
- Session tenant context is set with `app.current_company_id` and validated server-side.
- Administrative bypass may be enabled only through explicit server-controlled setting (`app.bypass_rls`).

## Baseline schema (Epics 1-2)

- `companies` (tenant registry) with `external_tenant_id` unique constraint.
- `fiscal_knowledge_base` with `(law_ref, company_id)` uniqueness and HNSW index on `embedding`.
- `inventory_transition` with PK `(sku_id, company_id)` and indexes on company, NCM, and transition risk.
- `transition_calendar`, `fiscal_audit_log`, `split_payment_events` all tenant-scoped.
- RLS enabled on all tenant-owned tables with `app.current_company_id`/`app.bypass_rls` guard.

## Ingestion and embedding storage

- Legal corpus is persisted in `fiscal_knowledge_base`.
- Vector ANN index target: `fiscal_knowledge_base.embedding`.
- Re-ingestion paths must preserve idempotency and dedup behavior.

## Sensitive fields and compliance

- Sensitive fields: `reform_taxes`, `vector_id`, `audit_confidence`, `llm_model_used`.
- Sensitive event trail: `RATE_GENERATED` audit records.
- Controls required: encryption at rest, RBAC for read/write access, audit logging, and retention/deletion policy support.
