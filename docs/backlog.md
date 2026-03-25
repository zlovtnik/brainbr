# Backlog

## Epic 1: Platform Bootstrap

1. Initialize repository skeleton and Kotlin project metadata.
2. Add Docker Compose with `db`, `redis`, `api`, `worker`.
3. Add configuration layer (`.env.example`, settings model).
4. Create Flyway baseline and first migration chain.

Acceptance:

- `docker compose up` boots all services.
- API health endpoint responds.
- Flyway migrates from zero to current version.

## Epic 2: Core Data and Tenancy

1. Create core tables and indexes.
2. Add unique and composite constraints.
3. Implement RLS policies for tenant-owned tables.
4. Set DB session variable from API auth/dependency layer.
5. Deliver all schema/database changes as Flyway migrations only.

Acceptance:

- Cross-tenant read/write blocked in DB tests.
- Composite-key CRUD paths pass.
- Auth/persistence tests cover happy-path and failure-path behavior.
- All DB/schema changes are delivered as Flyway migrations.
- Documentation updates are included in `README.md`, `HELP.md`, and `sql/README.md` for tenancy model and RLS policy behavior.

## Epic 3: Ingestion and Embeddings

1. Implement scraper with retry/dedup.
2. Implement article-aware chunking.
3. Implement batch embedding and upsert flow.
4. Mark superseded legal rows via controlled update path.

Acceptance:

- Re-ingestion updates existing law rows idempotently.
- Retrieval corpus populated and queryable.
- Test coverage includes retry failures, deduplication edge cases, chunking boundary conditions, and idempotent re-ingestion failure paths.
- Documentation is updated for ingestion architecture and embedding configuration in `README.md`, `HELP.md`, and `sql/README.md`.

## Epic 4: RAG Tax Audit Engine

1. Implement query embedding + top-K retrieval.
2. Implement structured prompt and strict JSON schema validation.
3. Persist `reform_taxes`, `vector_id`, `audit_confidence`, `llm_model_used`.
4. Emit `RATE_GENERATED` audit event.
5. Enforce security and compliance controls for persisted audit fields and event traces.

Acceptance:

- Known NCM case produces valid, persisted reform payload.
- Invalid LLM payload is rejected with traceable error.
- Persisted fields (`reform_taxes`, `vector_id`, `audit_confidence`, `llm_model_used`) and `RATE_GENERATED` records are protected with encryption-at-rest, RBAC access control, and audit logging.
- Data retention and deletion policies (GDPR/CCPA aligned) are defined and applied.
- PII definition for the tax audit domain is documented in `README.md`, `HELP.md`, and `sql/README.md`.
- Automated tests cover happy-path persistence plus failure-path rejection and traceability for invalid LLM payloads.

## Epic 5: API Surface

1. Implement inventory routes.
2. Implement audit explain/query routes.
3. Implement transition calendar/effective-rate routes.
4. Add pagination, filtering, and error model standardization.

Acceptance:

- OpenAPI docs match schema.
- Tenant-scoped API integration tests pass.

## Epic 6: Transition Engine and Reporting

1. Implement effective-rate calculator against transition calendar.
2. Create materialized view for fiscal impact.
3. Schedule refresh job in worker.
4. Add risk-score calculation routine.

Acceptance:

- Year-by-year regression tests pass.
- View refresh job runs and updates output.

## Epic 7: Quality, Security, and Ops

1. Add unit/integration suites.
2. Add CI pipeline with coverage.
3. Add structured logging and trace IDs.
4. Add operational runbooks and SLOs.

Acceptance:

- CI green on pull requests.
- Minimum coverage threshold enforced.

## Epic 8: Compliance Suite (Phase 3 ready)

1. Implement immutable audit trail paths.
2. Add split payment schema and API integration points.
3. Add explainability payload guarantees for audits.

Acceptance:

- Audit replay reproduces last generated rate context.
- Split payment events stored and queryable per tenant.
- Explainability payloads are validated against a defined schema and include required regulatory fields.
- Test acceptance: explainability data is complete, accurate, and auditable (schema validation passes, required fields exist, replayable/auditable traces are present).
