# Architecture

This document reflects the implementation in this repository as of 2026-04-06. It replaces earlier references to Spring Boot and Camel; the current runtime is Rust, SvelteKit, PostgreSQL, Redis, and a standalone Perl scraper.

## Architectural Goals

- Enforce tenant isolation at the application and database layers.
- Preserve explainability for every generated reform-tax result.
- Keep legal ingestion asynchronous and read paths fast.
- Separate tenant-owned operational data from legal-source knowledge data.
- Prefer append-only audit records for traceability.

## System Context

```mermaid
flowchart LR
    User["Operator"]
    Browser["Browser"]
    Web["SvelteKit Web App<br/>apps/web"]
    API["Rust API<br/>src/main.rs"]
    Worker["Rust Worker<br/>src/worker/main.rs"]
    Scraper["Perl Scraper<br/>scraper/"]
    Redis[("Redis Streams")]
    Postgres[("PostgreSQL 16 + pgvector")]
    OIDC["OIDC / JWKS issuer"]
    Models["OpenAI-compatible provider"]
    Sources["Regulatory sources"]

    User --> Browser
    Browser --> Web
    Web --> API
    API --> OIDC
    API --> Postgres
    Worker --> Redis
    Worker --> Postgres
    Worker --> Models
    Scraper --> Sources
    Scraper --> Redis
```

## Runtime Topology

```mermaid
flowchart TB
    subgraph Edge
        Browser["Browser"]
        Web["SvelteKit SSR UI"]
    end

    subgraph Services
        API["Axum API"]
        Worker["Tokio worker"]
        Scraper["Perl scraper"]
    end

    subgraph Data
        Redis[("Redis")]
        Postgres[("PostgreSQL + pgvector")]
    end

    subgraph External
        OIDC["JWT issuer / JWKS"]
        Models["Embedding + LLM provider"]
        Sources["Regulatory websites"]
    end

    Browser --> Web
    Web --> API
    API --> OIDC
    API --> Postgres
    Worker --> Redis
    Worker --> Postgres
    Worker --> Models
    Scraper --> Sources
    Scraper --> Redis
```

## Containers And Ownership

| Runtime | Code | Role |
| --- | --- | --- |
| Web UI | `apps/web` | Signed session cookie, route protection, SSR page loads, form actions, API proxying with bearer token |
| API | `src/api`, `src/main.rs` | Health endpoints, JWT validation, tenant resolution, inventory, transition, audit, ingestion, split-payment routes |
| Worker | `src/worker/main.rs`, `src/services/*` | Consumes Redis streams, runs embeddings and RAG work, persists explainability artifacts, refreshes reporting state |
| Scraper | `scraper/` | Polls external sources and writes `IngestionJob` payloads to Redis |
| PostgreSQL | `migrations/` | Tenant data, knowledge base, vector chunks, audit trail, explainability, transition tables, split-payment records |
| Redis | configured in `src/config/mod.rs` | Stream transport for ingestion and audit jobs plus retry bookkeeping |

## Core Domain Boundaries

### Tenant Operational Data

- `companies`
- `inventory_transition`
- `split_payment_events`
- `fiscal_audit_log`
- `audit_explainability_run`

These tables are tenant-scoped and accessed through row-level security using `app.current_company_id`.

### Knowledge And Retrieval Data

- `fiscal_knowledge_base`
- `fiscal_knowledge_chunk`

These tables back retrieval and explainability. The worker writes them during ingestion, and audit/query paths read them using pgvector similarity search.

### Reference Data

- `transition_calendar`

This is migration-seeded reference data for the 2026-2033 transition schedule.

## Security And Tenancy Model

- JWT bearer auth is enforced in Axum middleware when `APP_SECURITY_JWT_ENABLED=true`.
- JWKS can be loaded directly or discovered from the issuer metadata endpoint.
- Tenant identity is derived from the verified JWT tenant claim and then checked against `companies`.
- Request handlers open a transaction and call `set_config('app.current_company_id', ...)` before tenant-scoped queries.
- RLS is enabled on tenant-owned tables, and audit-oriented tables are append-only where appropriate.
- The SvelteKit app stores the JWT in a signed, HTTP-only session cookie and forwards it to the API on server-side requests.

## Flow Graphs

### 1. Authenticated Web Request

```mermaid
sequenceDiagram
    participant U as User
    participant B as Browser
    participant W as SvelteKit
    participant A as Axum API
    participant J as JWKS / OIDC
    participant P as PostgreSQL

    U->>B: Open /inventory
    B->>W: GET /inventory with session cookie
    W->>W: Read and verify signed cookie
    W->>A: GET /api/v1/inventory/sku<br/>Authorization: Bearer JWT
    A->>J: Resolve and validate signing key
    A->>P: Resolve tenant claim against companies
    A->>P: BEGIN + set app.current_company_id
    A->>P: SELECT inventory rows under RLS
    P-->>A: Tenant-scoped data
    A-->>W: JSON response
    W-->>B: Rendered page
```

### 2. Ingestion Pipeline

```mermaid
sequenceDiagram
    participant S as Regulatory source
    participant C as Scraper
    participant R as Redis queue_ingestion
    participant W as Worker
    participant M as Embedding provider
    participant P as PostgreSQL

    C->>S: Poll and normalize source material
    C->>R: XADD IngestionJob payload
    W->>R: XREADGROUP / XAUTOCLAIM
    W->>W: Chunk text and hash content
    W->>M: Embed chunk batch
    M-->>W: Embedding vectors
    W->>P: Upsert fiscal_knowledge_base
    W->>P: Upsert fiscal_knowledge_chunk
    W->>P: Append INGESTION_COMPLETE audit event
```

The ingestion path has two producers: the scraper for automated source polling and the authenticated API endpoint for manual/operator-submitted legislation.

### 3. RAG Audit Execution

```mermaid
sequenceDiagram
    participant Q as Audit producer
    participant R as Redis queue_audit
    participant W as Worker
    participant P as PostgreSQL
    participant M as Embedding + LLM provider

    Q->>R: XADD AuditJob
    W->>R: XREADGROUP / XAUTOCLAIM
    W->>P: Load SKU under tenant RLS
    W->>M: Embed query text
    W->>P: Vector search on fiscal_knowledge_chunk
    P-->>W: Top legal chunks
    W->>M: Generate structured reform tax payload
    M-->>W: JSON output
    W->>P: Insert audit_explainability_run
    W->>P: Update inventory_transition.reform_taxes, vector_id, confidence
    W->>P: Append RATE_GENERATED audit event
```

The audit worker path is fed by `AuditJob` payloads published to Redis; the manual re-audit endpoint is one of those producers.

### 4. Read-Time Explainability And Forecasting

```mermaid
flowchart LR
    SKU["Tenant SKU"]
    API["Axum API"]
    INV[("inventory_transition")]
    KB[("fiscal_knowledge_chunk / base")]
    ART[("audit_explainability_run")]
    CAL[("transition_calendar")]

    SKU --> API
    API --> INV
    API --> KB
    API --> ART
    API --> CAL
```

- `GET /api/v1/audit/explain/:sku_id` reads the stored `vector_id` from `inventory_transition`, then joins back to the knowledge tables to return the legal source behind the last audit.
- `GET /api/v1/audit/explain/:sku_id/artifact/latest` and `GET /api/v1/audit/explain/artifact/runs/:run_id` expose the persisted explainability artifact.
- `GET /api/v1/transition/calendar`, `GET /api/v1/transition/sku/:sku_id/effective-rate`, and `GET /api/v1/transition/sku/:sku_id/forecast` combine `transition_calendar` with stored `legacy_taxes` and `reform_taxes`.

## Data Ownership By Table

| Table | Primary writer | Primary readers |
| --- | --- | --- |
| `inventory_transition` | API inventory routes, worker audit enrichment | API inventory and transition routes |
| `fiscal_knowledge_base` | worker ingestion | worker retrieval, API explain/query joins |
| `fiscal_knowledge_chunk` | worker ingestion | worker retrieval, API explain/query joins |
| `audit_explainability_run` | worker audit path | API compliance/explainability routes |
| `fiscal_audit_log` | API routes and worker | audit/compliance review and traceability |
| `split_payment_events` | split-payment API route | split-payment list endpoint |
| `transition_calendar` | migrations | transition endpoints |

## Queue And Scheduling Model

- `queue_ingestion`
  - Implemented producers: scraper and `POST /api/v1/ingestion/jobs`.
  - Implemented consumer: worker ingestion loop.
- `queue_audit`
  - Implemented producer: `POST /api/v1/inventory/sku/:sku_id/re-audit`.
  - Implemented consumer: worker audit loop.
- `queue_reporting`
  - Configured but not currently used in a live producer/consumer path.
- Scheduled jobs inside the worker:
  - materialized-view refresh timer
  - stale shared-legislation re-ingestion scan
  - worker heartbeat logging

## Known Gaps And Mismatches

- The old docs described a Spring Boot and Apache Camel deployment. That is no longer accurate.
- The current SvelteKit app is a server-side proxy/BFF, not a direct browser-to-API SPA.
- Reporting is timer-driven today; `queue_reporting` is reserved for future background work.

## Operational Notes

- The API runs migrations on startup.
- Redis retry tracking uses dedicated keys per stream message for retry count and retry delay.
- Worker reclaim logic uses `XAUTOCLAIM` to take over idle pending messages.
- Model calls use an OpenAI-compatible base URL and support `MODEL_PROVIDER_MODE=mock` for non-live development paths.

## Where To Go Next

- [Data Model](data-model.md)
- [API Contract](api-contract.md)
- [Development Guide](development-guide.md)
- [Operations and Security](operations-security.md)
- [Runbooks and SLOs](runbooks-and-slos.md)
