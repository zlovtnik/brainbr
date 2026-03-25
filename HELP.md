# FiscalBrain-BR Help

## Tenancy and authorization quick reference

- Tenant scope is server-derived from verified auth/session context.
- `company_id` must never be accepted from client payloads, query params, or headers.
- Tenant isolation is enforced by API authorization and PostgreSQL RLS.

## Ingestion pipeline overview

- Source acquisition -> chunking -> embedding -> upsert into `fiscal_knowledge_base`.
- Re-ingestion must be idempotent and handle retry/dedup failure paths.
- Embedding model is configured via `EMBEDDING_MODEL`.

## RAG output contracts

- Canonical schemas:
  - `docs/schemas/legacy-taxes-v1.schema.json`
  - `docs/schemas/reform-taxes-v1.schema.json`
  - `docs/schemas/rag-output-v1.schema.json`
- Validate payloads with `validateLegacyTaxes`, `validateReformTaxes`, and `validateRagOutput` before persistence.

## Security and PII handling

- Sensitive persisted fields include `reform_taxes`, `vector_id`, `audit_confidence`, `llm_model_used`.
- `RATE_GENERATED` events require traceability and audit logging.
- PII definition for this domain: data that can identify a legal entity or user directly/indirectly (for example CNPJ-linked tenant identity, user identity metadata, and legal source attribution when tied to identifiable actors).
- Apply encryption-at-rest, retention windows, and deletion workflows aligned with GDPR/CCPA-style controls.
