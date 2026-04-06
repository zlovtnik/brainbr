# FiscalBrain-BR

Open-source fiscal engine for the Brazilian tax reform transition (EC 132/2023).

Built with Rust (Axum · Tokio · sqlx), PostgreSQL 16 + pgvector, Redis, and a SvelteKit web app.
The repo also includes a standalone Perl scraper service for automated regulatory ingestion.

## Stack

| Layer | Technology |
|---|---|
| API / Worker | Rust 1.75+, Axum 0.7, Tokio |
| Database | PostgreSQL 16 + pgvector, sqlx, Flyway migrations |
| Queue / Cache | Redis 7 (streams) |
| Embeddings / LLM | OpenAI (`text-embedding-3-small`, `gpt-4o`) |
| Web | SvelteKit 5 (`apps/web`) |
| Scraper | Perl 5.38+, Mojolicious, Minion, Redis |
| Infrastructure | Docker Compose |

## Quick start

```bash
cp .env.example .env
# Set DB_PASSWORD, OPENAI_API_KEY, and JWT config in .env
docker compose up --build
```

Health check: `GET http://localhost:8080/actuator/health`

## Environment variables (`.env.example`)

| Variable | Purpose |
|---|---|
| `DATABASE_URL` | PostgreSQL connection string |
| `DB_PASSWORD` | Postgres password |
| `OPENAI_API_KEY` | Embedding + LLM calls |
| `REDIS_URL` | Redis connection |
| `APP_SECURITY_JWT_ISSUER_URI` | JWT issuer (or use `JWK_SET_URI`) |
| `APP_SECURITY_JWT_TENANT_CLAIM` | Claim key for tenant ID |
| `APP_BYPASS_RLS_ENABLED` | `false` in production |

## Services

- `api` — REST API on `:8080`, runs migrations on startup
- `worker` — background processor (ingestion, RAG audits, reporting)
- `scraper` — scheduled regulatory source poller that writes shared `IngestionJob` payloads to `queue_ingestion`
- `db` — PostgreSQL 16 with pgvector
- `redis` — queue and cache

## Source layout

```
src/
  api/          REST routes and middleware
  services/
    rag/        vector retrieval + LLM audit
    transition/ EC 132/2023 effective-rate calculator
    ingestion/  chunking, embedding, upsert
    splitpayment/
    audit/
  db/           sqlx models and pool
  config/       AppConfig from env
  worker/       background job entrypoint
migrations/     SQL migration files (applied by sqlx at startup)
apps/web/       SvelteKit frontend
scraper/        Perl regulatory scraper service (Minion + Redis)
```

## Running tests

```bash
cargo test                  # unit + integration (requires running db/redis)
cd apps/web && bun test     # frontend unit tests
cd apps/web && bun e2e      # Playwright e2e
cd scraper && prove -lr t/  # scraper unit/integration tests
```

## Documentation

- [Architecture](docs/architecture.md)
- [Data Model](docs/data-model.md)
- [API Contract](docs/api-contract.md)
- [Development Guide](docs/development-guide.md)
- [Backlog](docs/backlog.md)
- [Testing and Quality](docs/testing-quality.md)
- [Operations and Security](docs/operations-security.md)
- [Runbooks and SLOs](docs/runbooks-and-slos.md)
- [ADR-001 Tenant Isolation Baseline](docs/adr-001-tenant-and-traceability-baseline.md)
- [RAG Schemas](docs/schemas/)
- [SQL README](sql/README.md)
