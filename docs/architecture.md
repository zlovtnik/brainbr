# Architecture

## Runtime topology

- `api` service: Spring Boot REST application.
- `worker` service: Spring Boot worker profile with Apache Camel routes for ingestion, RAG audits, and maintenance jobs.
- `db` service: PostgreSQL 16 with `pgvector`.
- `redis` service: queue/cache integration for worker workflows.

## Core modules

- `src/ingestion`: source acquisition, chunking, embedding, upsert.
- `src/rag`: retrieval, prompt construction, LLM invocation, response validation.
- `src/fiscal`: transition calendar and effective-rate calculations.
- `src/api`: REST routes and dependency wiring.
- `src/models`: persistence/domain models and request/response contracts.

## Data flow

1. Law text is ingested into `fiscal_knowledge_base` with tenant scoping and provenance metadata.
2. SKU is created in `inventory_transition` and queued for automated tax reform analysis.
3. RAG audit retrieves top-K law chunks, generates `reform_taxes`, validates strict schemas, and stores explainability payload (`vector_id`, `law_ref`, `llm_model_used`, `audit_confidence`).
4. API exposes tenant-scoped inventory views, explainability, and transition calculations.
5. Materialized dashboard view is refreshed by scheduled worker and consumed by reporting endpoints.

Enabled capabilities include automated reform impact analysis, auditable explainability responses, and tenant-safe retrieval over legal corpus.

## Scaling controls

- Queue separation: `queue_ingestion`, `queue_audit`, `queue_reporting`.
- HNSW vector index for ANN search with periodic maintenance windows.
- Index strategy for tenant and NCM-heavy lookup patterns.
- Avoid global full refreshes where incremental refresh is feasible.

## Security controls

- Authentication and authorization:
  - Supported auth mechanisms are OAuth2/JWT bearer and API keys for service clients.
  - Authorization uses tenant-scoped RBAC/scopes aligned with endpoint permissions.
- Tenant identity derivation:
  - `app.current_company_id` must be derived server-side from verified auth/session context only.
  - Client payload/query/header tenant values are never authoritative.
- Transport and storage protection:
  - TLS required for all inter-service communication.
  - Encryption at rest required for PostgreSQL data, Redis persistence snapshots (if enabled), backups, and sensitive log storage.
- Input and API security:
  - Validate and sanitize user-supplied input for SQL injection, XSS, and malformed payloads.
  - Maintain CORS allowlist, API authentication, and per-tenant rate limiting.
- LLM-specific controls:
  - Prompt-injection mitigation rules on retrieval/prompt composition.
  - PII redaction before prompt/log persistence.
  - Harmful-content filtering and safe-fail response path.
- Secrets and auditability:
  - Secrets sourced from secret manager/environment only.
  - Rotation policy applies to DB credentials, API keys, and encryption keys with audit trail for every rotation event.
- Network posture:
  - VPC segmentation, service firewalls, and least-privilege network/service access.
- Change management rule:
  - Any change to authentication, persistence, or migration strategy must update this architecture document in the same change set.

## Operational readiness

- Architecture diagrams:
  - Keep C4-style system/container diagrams current and linked from this section.
  - See [Runtime topology](#runtime-topology) and [Core modules](#core-modules) for component mapping.
- Monitoring and alerting:
  - Core metrics: request latency (p50/p95/p99), error rates, queue depth, worker failure rates, RAG validation failures, DB connection saturation.
  - SLO starter thresholds: API availability >= 99.9% monthly, `POST /inventory/sku` p95 < 300ms ack time, explain endpoint p95 < 500ms.
  - Alerting thresholds: sustained p95 breaches over 10 minutes, ingestion/re-audit dead-letter growth, repeated schema-validation failures.
- Disaster recovery:
  - Postgres: daily full backups + WAL/incremental backups, validated restore drills.
  - Redis: snapshot/AOF strategy per environment with documented restore process.
  - Target RTO: 60 minutes. Target RPO: 15 minutes.
- Performance targets:
  - API read endpoints p95 < 500ms under nominal load.
  - Audit query endpoint p95 < 1500ms for default `k=5`.
  - Worker throughput target documented by queue type and reviewed each release.

## Development workflow

- Local setup:
  1. Copy `.env.example` to `.env` and set required secrets.
  2. Start stack via Docker Compose.
  3. Run migrations and execute tests before opening PR.
- Testing strategy:
  - Unit tests for business rules, validators, and transition math.
  - Integration tests for DB/RLS + API routes + migration behavior.
  - End-to-end tests for ingestion -> RAG -> explainability flows.
  - See [Data flow](#data-flow) and [Security controls](#security-controls) for required assertions.
- CI/CD overview:
  - PR checks run lint/compile/test/migration verification.
  - Main branch merges trigger container build and staged deployment pipeline.
  - Production promotion requires SLO/alert posture review.
- Known limitations:
  - Endpoint surface is being delivered incrementally by phase.
  - Some compliance controls are phase-gated and tracked in backlog.
- Glossary:
  - `NCM`: Brazilian product classification code used in fiscal rules.
  - `SKU`: stock keeping unit identifier in tenant inventory.
  - `reform_taxes`: structured tax payload generated by RAG audit for reform regime fields.
