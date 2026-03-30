# CodeRabbit Review Guide

Purpose: give CodeRabbit the project context it needs to review both the Kotlin/Spring backend and the Svelte 5 frontend. Point the CLI here: `coderabbit review --agent -t uncommitted --config ../../docs/coderabbit.md`.

## Repository map
- Backend (API/worker): Kotlin + Spring Boot in the repo root (`src/**`, `sql/**`, Gradle build files).
- Frontend (UI): SvelteKit + TypeScript in `apps/web/**`.
- Shared docs worth skimming: `docs/development-guide.md`, `docs/architecture.md`, `docs/testing-quality.md`, `docs/operations-security.md`, RAG schemas in `docs/schemas/`.

## Non‑negotiable product constraints
- All data access is tenant-scoped by `company_id`; never trust client-supplied tenant identifiers and never ship cross-tenant reads/writes.
- Row-level security must be enforced with both `USING` and `WITH CHECK` clauses; migrations go through Flyway only (`sql/`).
- RAG payloads must be schema-validated before persistence using `validateLegacyTaxes`, `validateReformTaxes`, and `validateRagOutput` against the canonical schemas in `docs/schemas/`.
- Every generated rate must carry `vector_id` and `law_ref`; logging should include `request_id` and `company_id`.

## Backend review checklist (Kotlin/Spring)
- **Security/tenancy**: Controllers and services must derive tenant from trusted identity (JWT host/issuer or server lookup), never from request bodies. All DB queries include `company_id`; repository logic should not bypass RLS. Avoid any string concatenated SQL; prefer prepared statements/ORM safety.
- **Validation & errors**: Use Bean Validation on inputs; respond with clear 4xx on validation/auth failures. Do not leak internal exceptions in 5xx bodies.
- **Persistence/migrations**: New columns require defaults/migration backfill; ensure unique keys: `(sku_id, company_id)` for inventory and `(law_ref, company_id)` for knowledge. UUIDs via `gen_random_uuid()`. Keep down migrations compatible and idempotent.
- **Pipelines**: In ingestion/audit flows, preserve idempotency and traceability; queue/worker steps must propagate tenant context and request correlation.
- **Observability**: Structured logs with request correlation; health/info actuators should stay enabled. Prefer explicit timeouts/retries on outbound calls.
- **Testing**: Favor JUnit 5 + Testcontainers for integration, focused unit tests otherwise. Command: `./gradlew test` (respects JUnit Platform).

## Frontend review checklist (SvelteKit + TS)
- **Type safety & data flow**: Keep server/client loads typed; prefer `svelte-check`/TypeScript coverage. Handle fetch errors with user-friendly states; never trust client-provided tenant IDs.
- **Auth/session flows**: Ensure JWT handling aligns with backend expectations; tests rely on Playwright helpers (see `apps/web/e2e/helpers.js`) and mock API reset.
- **Accessibility & UX**: Maintain semantic labels, focus management (`#main-content` focus expectation), and ARIA for form controls; follow WCAG 2.2 patterns.
- **State & forms**: Avoid mutable shared state across tenants; guard against stale cache between users. Validate inputs client-side before POSTing.
- **Build/runtime checks**: Commands: `bun run check` (type), `bun run lint` (lint + prettier), `bun run test` (unit via Vitest), `bun run test:e2e` (Playwright; requires mock API up).

## Reviewer workflow for CodeRabbit
1) Identify whether changes touch backend, frontend, or both; apply the relevant checklist above.
2) Verify new/changed endpoints or queries maintain tenancy, validation, and schema contracts; ensure matching migrations and tests exist.
3) For UI changes, confirm accessibility expectations and that mocked/e2e flows still work (reset mock API, auth bootstrap).
4) Recommend targeted tests (unit/integration/e2e) and point to the exact command that should be run from the lists above.
5) Flag missing docs when behavior or env vars change; prefer updating `docs/development-guide.md` or feature-specific ADRs.
