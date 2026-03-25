# ADR-001: Tenant Isolation and Rate Traceability Baseline

## Status

Accepted

## Context

FiscalBrain-BR must support multi-tenant SaaS usage and legal audit traceability for every generated tax rate.

## Decision

1. All tenant-owned entities use `company_id` and tenant-aware composite keys where applicable. `company_id` is ALWAYS derived on the server and never accepted as authoritative from client-supplied inputs. Trusted derivation sources are: validated authentication token/claims, verified subdomain/host mapping, mTLS certificate identity, or a server-side lookup from a trusted session. Any inferred tenant context must be validated server-side before use. Do not accept `company_id` from client payloads, query params, or headers.
2. PostgreSQL RLS is mandatory on tenant-owned tables with both `USING` and `WITH CHECK`.
3. Every generated `reform_taxes` payload must store `vector_id`, `llm_model_used`, and `audit_confidence`.
4. Every rate mutation emits `fiscal_audit_log` event with actor and old/new values.

Enforcement note: authentication middleware and request parsing layers must populate validated `company_id` on the server before any tenant-aware database operation or composite key construction.

## Consequences

- Stronger isolation and audit guarantees.
- Slightly higher complexity in service/repository method signatures.
- Additional mandatory tests for tenancy and traceability contracts.
