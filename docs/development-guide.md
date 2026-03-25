# FiscalBrain-BR Development Guide

## Purpose

This guide turns the technical specification into an implementation-ready plan with explicit defaults for architecture, tenancy, audit, and scalability.

## Delivery scope

- Baseline target: complete Phase 1 and Phase 2 first.
- Design quality target: Phase 3 requirements must be compatible with Phase 1/2 decisions.
- Phase 4 is planned but not blocking initial release.

## Phases

### Phase 1 - Platform and tenancy baseline

- Features:
  - Platform bootstrap, baseline schema/migrations, tenant keys and RLS foundation.
- Acceptance criteria:
  - Environment boots locally, migrations apply, tenant-scoped CRUD paths and baseline observability are working.
- Dependencies:
  - Docker runtime, PostgreSQL + Redis, Flyway baseline, environment secrets.

### Phase 2 - Ingestion and core audit pipeline

- Features:
  - Ingestion, chunking, embedding, retrieval, and schema-validated audit persistence.
- Acceptance criteria:
  - Re-ingestion is idempotent, RAG outputs persist with explainability metadata, failure paths are tested.
- Dependencies:
  - Phase 1 tenancy guarantees, vector index, model credentials, queue workers.

### Phase 3 - Compliance and design quality target

- Features:
  - Compliance suite, explainability payload guarantees, split payment integration points, advanced security controls.
- Acceptance criteria:
  - Explainability payloads pass schema checks and are auditable/replayable; compliance artifacts are tenant-safe.
- Dependencies:
  - Phase 1/2 contract stability, audit trail completeness, operations/security controls.

### Phase 4 - Planned but not blocking

- Features:
  - Extended optimization and advanced non-critical capabilities.
- Acceptance criteria:
  - Improvements are backward compatible and do not break Phase 1-3 guarantees.
- Dependencies:
  - Stable production telemetry and validated operational readiness.

## Non-negotiable engineering constraints

- Every read/write path must be tenant-scoped by `company_id`.
- Every generated rate must be explainable through `vector_id` and `law_ref`.
- Schema changes must ship through Flyway migrations only.
- RAG output must be validated against strict schemas before persistence.
  - Canonical schemas:
    - `docs/schemas/legacy-taxes-v1.schema.json`
    - `docs/schemas/reform-taxes-v1.schema.json`
    - `docs/schemas/rag-output-v1.schema.json`
  - Required validator calls before persistence: `validateLegacyTaxes`, `validateReformTaxes`, and `validateRagOutput`.
- Background processing must isolate queue types (`ingestion`, `audit`, `reporting`).

## Canonical contracts (locked defaults)

- Tax/boolean field naming:
  - Boolean flags must use the `is_<noun>` pattern (example: `is_taxable`).
  - Do not use a bare `is` field and do not use `is_rate_<...>`.
  - DB example: `is_taxable BOOL NOT NULL`.
  - JSON example: `"is_taxable": true`.
  - Rate values must be separate numeric fields (example: `tax_rate`).
  - Compatibility note: existing `is_rate_*` fields must be migrated to `is_<noun>` with backward-compatible mapping during transition.
- Composite entity identity for inventory: `(sku_id, company_id)`.
- Knowledge uniqueness: `(law_ref, company_id)` unique index.
- UUID generation: `pgcrypto` + `gen_random_uuid()`.
- Row-level security: both `USING` and `WITH CHECK`.

## Build order

1. Platform bootstrap (Docker, env, app skeleton, migration baseline).
2. Core schema + tenancy and RLS.
3. Ingestion and embedding pipeline.
4. RAG generation and explain audit flow.
5. Transition calculator and transition endpoints.
6. Reporting view and scheduled refresh.
7. Hardening: tests, observability, security controls.

## Definition of done (MVP+Core)

- Core endpoints return tenant-correct data.
- RAG rate generation writes `reform_taxes`, `vector_id`, `audit_confidence`.
- Transition endpoint returns year-specific effective burden.
- Audit endpoint returns source legal paragraph + model metadata.
- Unit and integration suites pass in CI.
