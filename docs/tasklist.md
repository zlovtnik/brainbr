# FiscalBrain-BR — Implementation Tasklist

## Legend
- ✅ Done
- 🔄 In progress / partial
- ⬜ Not started

---

## Epic 1 — Platform Bootstrap
- ✅ Repository skeleton and Cargo workspace
- ✅ Docker Compose (`db`, `redis`, `api`, `worker`)
- ✅ `.env.example` and `AppConfig` with full env parsing
- ✅ Flyway-compatible SQL migrations (run via sqlx migrate)
- ✅ API health endpoints (`/actuator/health`, liveness, readiness)
- ✅ `/api/v1/platform/info` endpoint

## Epic 2 — Core Data and Tenancy
- ✅ `companies`, `inventory_transition`, `transition_calendar` tables
- ✅ `fiscal_knowledge_base` with `pgvector` embedding column
- ✅ Composite PK and indexes on `inventory_transition`
- ✅ RLS policies on all tenant-owned tables
- ✅ `set_config('app.current_company_id')` wired in tenant middleware
- ✅ `external_tenant_id` lookup path in tenant resolver
- ✅ Cross-tenant isolation enforced at DB level

## Epic 3 — Ingestion and Embeddings
- ✅ `POST /api/v1/ingestion/jobs` — enqueue ingestion job
- ✅ Article-aware chunking (`chunk_text` with overlap)
- ✅ Content hash and idempotent upsert into `fiscal_knowledge_base`
- ✅ `fiscal_knowledge_chunk` table with per-chunk storage
- ✅ **Batch embedding via OpenAI `text-embedding-3-small`** ← wired in `IngestionService::process_job`
- ✅ Embeddings persisted to `fiscal_knowledge_chunk.embedding` (VECTOR 1536)
- ✅ HNSW index on `fiscal_knowledge_chunk.embedding` (`migration 20240101000011`)
- ✅ `INGESTION_COMPLETE` audit event emitted
- ✅ Supersession partial unique index (`migration 20240101000009`)
- ⬜ Automated scraper / crawler for Brazilian legislation sources (CONFAZ, Receita Federal, SEFAZ portals)
- ⬜ Scheduled re-ingestion job for legislation updates
- ⬜ `state` + `ncm_scope` metadata convention enforced at ingestion time (currently optional via `tags`)

## Epic 4 — RAG Tax Audit Engine
- ✅ `RagService::embed` — single text embedding via OpenAI
- ✅ `RagService::embed_batch` — batch embedding for ingestion
- ✅ `RagService::vector_search` — top-K cosine search with state filter + global fallback
- ✅ `RagService::audit` — full loop: embed → retrieve → prompt → LLM → validate
- ✅ Structured prompt in Portuguese with EC 132/2023 / LC 68/2024 context
- ✅ GPT-4o called with `response_format: json_object` and `temperature: 0`
- ✅ `validate_reform_taxes` — strict schema check (required fields, types, ranges)
- ✅ `AuditService::process_audit_job` — full RAG loop wired to worker queue
- ✅ `reform_taxes`, `vector_id`, `audit_confidence`, `llm_model_used`, `last_llm_audit` persisted on `inventory_transition`
- ✅ `transition_risk_score` computed via `compute_risk_score` and persisted
- ✅ `audit_explainability_run` row written with full replay context and artifact digest
- ✅ `RATE_GENERATED` audit event emitted with `run_id` linkage
- ✅ `AuditService::query` — semantic search endpoint wired to vector search
- ✅ Mock provider mode (`MODEL_PROVIDER_MODE=mock`) for local dev without OpenAI key
- 🔄 **[CRITICAL] Prompt injection mitigation** — sanitise all user-supplied inputs before prompt composition; acceptance criteria: `sanitize_input` applied to all RAG prompt parameters, fuzz tests pass, no control chars or brace injection possible
- 🔄 **[CRITICAL] PII redaction before prompt and log persistence** — strip CPF/CNPJ/email/phone from `description`, `replay_context`, and `rag_output` before storage; acceptance criteria: PII redaction pipeline in place, integration test confirms no PII in persisted artifacts
- 🔄 **[CRITICAL] Harmful-content / refusal detection on LLM response** — detect and reject refusal or off-topic LLM output before persisting; acceptance criteria: refusal patterns matched, job routed to DLQ with `AUDIT_REFUSAL` event
- ⬜ Legacy tax extraction from legislation via RAG (currently manual field)
- 🔄 **[CRITICAL] Confidence threshold gate** — configurable minimum confidence (`AUDIT_MIN_CONFIDENCE`); route low-confidence jobs to DLQ with `AUDIT_LOW_CONFIDENCE` event; acceptance criteria: threshold enforced in `process_audit_job`, DLQ routing tested

## Epic 5 — API Surface
- ✅ `GET /api/v1/inventory/sku` — paginated list with search and sort
- ✅ `POST /api/v1/inventory/sku` — upsert SKU
- ✅ `GET /api/v1/inventory/sku/:sku_id` — get single SKU
- ✅ `PUT /api/v1/inventory/sku/:sku_id` — update SKU
- ✅ `DELETE /api/v1/inventory/sku/:sku_id` — soft delete
- ✅ `POST /api/v1/inventory/sku/:sku_id/re-audit` — enqueue re-audit
- ✅ `GET /api/v1/audit/explain/:sku_id` — latest reform taxes + source
- ✅ `GET /api/v1/audit/explain/:sku_id/artifact/latest` — full explainability artifact
- ✅ `GET /api/v1/audit/explain/artifact/runs/:run_id` — artifact by run ID
- ✅ `POST /api/v1/audit/query` — semantic search over knowledge base
- ✅ `GET /api/v1/transition/calendar` — full transition calendar
- ✅ `GET /api/v1/transition/sku/:sku_id/effective-rate?year=` — single-year blended burden
- ✅ **`GET /api/v1/transition/sku/:sku_id/forecast`** — all years 2026–2033 in one call ← new
- ✅ `POST /api/v1/split-payment/events` — create split payment event (idempotent)
- ✅ `GET /api/v1/split-payment/events` — list events with filters
- ✅ `POST /api/v1/ingestion/jobs` — submit legislation ingestion job
- ⬜ `GET /api/v1/fiscal-impact/dashboard` — materialized view reporting endpoint
- ⬜ OpenAPI spec (`docs/openapi.yaml`) updated to reflect all current routes
- ⬜ Standardised pagination envelope on all list endpoints

## Epic 6 — Transition Engine and Reporting
- ✅ `transition_calendar` seeded with 2026–2033 weights (LC 68/2024)
- ✅ `blended_burden` calculator (legacy × weight + reform × weight)
- ✅ `compute_risk_score` (delta + confidence composite)
- ✅ `mv_fiscal_impact` materialized view (legacy burden, reform burden, delta, risk score)
- ✅ Worker refresh job with advisory lock (`REFRESH MATERIALIZED VIEW CONCURRENTLY`)
- ✅ `ForecastResponse` — full 2026–2033 year-by-year tax preview per SKU
- ⬜ `GET /api/v1/fiscal-impact/dashboard` endpoint consuming `mv_fiscal_impact`
- ⬜ Portfolio-level aggregate: total tax delta across all SKUs for a tenant
- ⬜ Risk-score histogram / distribution endpoint

## Epic 7 — Quality, Security, and Ops
- ✅ Structured logging with `tracing` + JSON format option
- ✅ Request ID propagation via middleware
- ✅ Worker heartbeat and graceful shutdown via broadcast channel
- ✅ DLQ routing for undeserializable jobs
- ✅ Retry-on-redeliver for failed processing (no explicit ack on error)
- ✅ `AppConfig` debug redacts DB URL and OpenAI key
- ⬜ Unit tests for `RagService::validate_reform_taxes`
- ⬜ Unit tests for `chunk_text` edge cases (already partially covered)
- ⬜ Integration tests: ingestion → embedding → vector search round-trip
- ⬜ Integration tests: full RAG audit job with mock provider
- ⬜ Integration tests: forecast endpoint year-by-year values
- ⬜ CI pipeline coverage threshold enforcement
- ⬜ Distributed trace IDs (OpenTelemetry / `tracing` spans)
- ⬜ Per-tenant rate limiting on API endpoints
- ⬜ TLS termination config documented for production deployment

## Epic 8 — Compliance Suite
- ✅ `audit_explainability_run` table (append-only, immutable trigger)
- ✅ `fiscal_audit_log` append-only with mutation prevention trigger
- ✅ `split_payment_events` with idempotency key and restricted mutation
- ✅ Artifact digest (SHA-256 of `rag_output`) for tamper detection
- ✅ `replay_context` stored for full audit replay
- ✅ RLS on `audit_explainability_run` and `split_payment_events`
- 🔄 **[CRITICAL] Data retention / deletion policy (LGPD/GDPR)** — define and implement explicit retention timelines for `inventory_transition`, `fiscal_knowledge_base`, `audit_explainability_run`; add automated deletion workflows and document policy; acceptance criteria: retention policy doc merged, deletion job implemented and tested
- 🔄 **[CRITICAL] Encryption-at-rest verification for `reform_taxes` and `rag_output`** — validate column-level encryption, key management, and access controls for these columns before broader deployment; acceptance criteria: encryption verified in staging, key rotation documented
- ⬜ RBAC scope enforcement beyond tenant isolation (e.g. read-only auditor role)
- ⬜ Audit replay endpoint: re-run RAG with stored `replay_context` and compare

---

## Immediate Next Steps (priority order)

1. 🔄 **[CRITICAL] Prompt injection mitigation** — strip/escape user-supplied text before prompt composition ← already partially implemented in `sanitize_input`
2. 🔄 **[CRITICAL] PII redaction pipeline** — redact CPF/CNPJ/email before prompt and log persistence
3. 🔄 **[CRITICAL] Harmful-content / refusal detection** — detect LLM refusals and route to DLQ with `AUDIT_REFUSAL` event
4. 🔄 **[CRITICAL] Confidence threshold gate** — configurable minimum confidence; route low-confidence jobs to DLQ with `AUDIT_LOW_CONFIDENCE` event
5. 🔄 **[CRITICAL] Data retention / deletion policy (LGPD/GDPR)** — retention timelines, deletion workflows, and automation for all tenant-owned tables
6. 🔄 **[CRITICAL] Encryption-at-rest verification** — validate `reform_taxes` and `rag_output` column encryption, key management, and access controls
7. ⬜ **Automated legislation scraper** — crawl CONFAZ, Receita Federal, SEFAZ-SP/RJ/MG portals; tag with `state` + `ncm_scope` in metadata
8. ⬜ **`GET /fiscal-impact/dashboard`** — expose `mv_fiscal_impact` via API
9. ⬜ **Integration test suite** — ingestion → embed → RAG audit → forecast round-trip with mock provider
10. ⬜ **Legacy tax RAG extraction** — extend audit prompt to also return `legacy_taxes` fields from legislation
11. ⬜ **OpenAPI spec sync** — update `docs/openapi.yaml` with forecast, query, and ingestion routes
