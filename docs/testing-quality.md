# Testing and Quality

## Test pyramid

- Unit tests: chunking, calculator math, schema validation, parser behavior.
- Integration tests: DB + vector search + API routes.
- End-to-end workflow tests: ingestion -> RAG -> explain endpoint.

## Must-have test scenarios

1. Tenant isolation:
   - same `sku_id` in two tenants does not leak or overwrite.
1a. Tenant isolation failure path:
   - unauthorized cross-tenant access attempts are rejected and audited.

2. Transition math:
   - burden outputs for representative SKUs from 2026 to 2033.
2a. Transition math failure path:
   - invalid year ranges and boundary years outside 2026-2033 return validation errors.

3. RAG contract:
   - invalid JSON or missing fields is rejected and logged.
3a. RAG contract failure path:
   - invalid input types, field omissions, and malformed nested payloads fail schema validation before persistence.

4. Upsert idempotency:
   - repeated law ingestion updates row without duplicates.
4a. Upsert idempotency failure path:
   - constraint violations and duplicate-collision conditions are handled deterministically with traceable errors.

5. Explainability:
   - every generated rate has resolvable `vector_id` and `law_ref`.
5a. Security failure paths:
   - authentication/authorization failures are denied with standard error responses.
   - injection attempts (SQL injection/XSS-style payloads) are sanitized/rejected and logged.

## Quality gates

- Lint + type checks.
- Unit + integration tests in CI.
- Coverage report with threshold (recommended initial threshold: 80% on core modules).
- Migration checks for upgrade/downgrade integrity.

## Performance baseline checks

- Retrieval latency p95 under realistic corpus sample.
- Effective-rate endpoint p95 under concurrent load.
- Dashboard view refresh time tracked over growth.
