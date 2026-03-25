# Operations and Security

## Environment model

- `development`: local Docker + test API keys.
- `staging`: production-like infra with scrubbed data.
- `production`: managed secrets, strict auditing, monitored workers.

## Required operational controls

- Structured logs with `request_id`, `company_id`, and job IDs.
- Metrics: API latency, queue depth, worker failures, embedding/LLM errors.
- Alerting: failed ingestion runs, repeated RAG parse failures, MV refresh failures.

## Security controls

- Enforce least-privilege DB roles by service.
- Apply RLS to tenant tables.
- Encryption:
  - Encryption at rest for databases, backups, and security-relevant logs.
  - TLS 1.2+ for all in-transit traffic between clients and services and between internal services.
- Authentication and authorization:
  - Supported mechanisms: API keys, OAuth2, JWT bearer tokens.
  - Authorization model: RBAC + tenant isolation enforced by API checks and DB RLS.
- API rate limiting:
  - Per-tenant quotas and endpoint-specific throttling.
  - Exceeded quotas return throttling response with retry guidance (`Retry-After`).
- Secret rotation:
  - Rotate DB credentials, API keys, and encryption keys on a defined schedule and on incident response.
  - Maintain auditable rotation records.
- Never log secrets or full sensitive payloads.
- Validate all external inputs (`law_identifier`, `sku`, `ncm_code`) with strict schema rules.

## Compliance and audit controls

- Immutable `fiscal_audit_log` append-only writes.
- Capture actor, model, timestamp, and source legal reference per rate change.
- Preserve source URL/publication metadata for legal provenance.

## SLO starter targets

- API availability: 99.9% monthly.
- `POST /inventory/sku` acknowledge time: p95 < 300ms (async processing).
- RAG audit completion time: p95 < 60s per SKU under nominal load.
- Audit explain endpoint: p95 < 500ms.
