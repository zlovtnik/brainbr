# FiscalBrain-BR

Open-source fiscal engine for the Brazilian tax reform transition (EC 132/2023), built with Kotlin, Spring Boot, Apache Camel, PostgreSQL/pgvector, and Redis.

## Stack baseline (Epic 1)

- Kotlin + Gradle (KTS)
- Spring Boot API
- Apache Camel worker profile
- PostgreSQL 16 + pgvector
- Flyway migrations
- Docker Compose (`db`, `redis`, `api`, `worker`)

## Quick start

1. Copy env file:
   - `cp .env.example .env`
   - Set JWT config in `.env`:
     - `APP_SECURITY_JWT_JWK_SET_URI=<your-jwks-uri>` or `APP_SECURITY_JWT_ISSUER_URI=<your-issuer>`
   - Set `DB_PASSWORD` and optional `DB_POOL_MAX`.
   - Set `APP_SECURITY_JWT_TENANT_CLAIM` if your tenant claim differs.
2. Build and run services:
   - `docker compose -f docker/docker-compose.yml up --build`
3. Check API:
   - `GET http://localhost:8080/actuator/health`
   - `GET http://localhost:8080/api/v1/platform/info`
4. Run migrations locally (optional): `./gradlew flywayMigrate`
5. Verify RLS: run integration tests `./gradlew test` (uses Testcontainers).

## Documentation index

- [Development Guide](docs/development-guide.md)
- [Architecture](docs/architecture.md)
- [Data Model](docs/data-model.md)
- [API Contract](docs/api-contract.md)
- [Backlog](docs/backlog.md)
- [Testing and Quality](docs/testing-quality.md)
- [Operations and Security](docs/operations-security.md)
- [Runbooks and SLOs](docs/runbooks-and-slos.md)
- [Interactive Svelte 5 Project Guide](docs/svelte5-interactive-project.md)
- [ADR-001 Tenant Isolation Baseline](docs/adr-001-tenant-and-traceability-baseline.md)
- [HELP](HELP.md)
- [SQL README](sql/README.md)
- [RAG Schemas](docs/schemas/)
