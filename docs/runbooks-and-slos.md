# Runbooks and SLOs

## Service level indicators and targets

- API availability: monthly success ratio for `/api/v1/**` excluding `401/403` auth failures, target `>= 99.9%`.
- Inventory write acknowledge latency: `POST /api/v1/inventory/sku` p95 target `< 300ms`.
- Explainability read latency: `GET /api/v1/audit/explain/**` p95 target `< 500ms`.
- Audit completion latency: queue enqueue-to-complete for audit jobs p95 target `< 60s`.
- Worker health: no sustained queue backlog growth for ingestion/audit/reporting streams.

## Alert thresholds

- API error spike: 5xx ratio `> 2%` for 10 minutes.
- Queue backlog: stream pending messages above normal baseline for 15 minutes.
- DLQ growth: any continuous dead-letter growth over 5 minutes.
- MV refresh failures: 2 consecutive failures of `mv_fiscal_impact` refresh job.
- Compliance inconsistency: explainability artifact fetch failures or schema mismatch events > 0 in 15 minutes.

## Runbook: API error spike

- Trigger: elevated 5xx for inventory/audit/split-payment endpoints.
- Immediate actions:
  - Inspect latest API logs filtered by `request_id` and `company_id`.
  - Confirm dependency health (`db`, `redis`, provider mode).
  - Validate recent deploy/config changes.
- Mitigation:
  - Roll back the last release if error rate remains above threshold.
  - Disable non-critical worker timers if DB contention is detected.
- Recovery check:
  - 5xx ratio back under threshold for 15 minutes.
  - p95 latency recovers to SLO.

## Runbook: Queue backlog or DLQ growth

- Trigger: pending queue depth increasing or DLQ receives repeated events.
- Immediate actions:
  - Check worker heartbeat and route error logs.
  - Inspect failed payload identifiers (`job_id`, `company_id`, `request_id`).
  - Verify Redis health and consumer group lag.
- Mitigation:
  - Scale worker replicas if failures are transient throughput limits.
  - Patch malformed payload producer and replay from DLQ after fix.
- Recovery check:
  - Queue depth trends down.
  - No new DLQ items for 10 minutes.

## Runbook: Materialized view refresh failure

- Trigger: transition refresh route logs errors or stale `mv_fiscal_impact` age.
- Immediate actions:
  - Review worker logs for `route_id=transition-refresh-route`.
  - Verify advisory lock behavior and DB connectivity.
  - Check for blocking transactions on `mv_fiscal_impact`.
- Mitigation:
  - Execute manual refresh in DB maintenance window.
  - If lock is stuck, clear blocker transaction before retry.
- Recovery check:
  - Refresh completes and updated timestamps advance.

## Runbook: Compliance and audit artifact inconsistency

- Trigger: explainability lookup failures, missing replay context, or artifact schema mismatch.
- Immediate actions:
  - Query `audit_explainability_run` by `sku_id` and latest `created_at`.
  - Verify linked `fiscal_audit_log` entries (`run_id`, `artifact_version`, `artifact_digest`).
  - Validate payload with current explainability JSON schema.
- Mitigation:
  - Re-run audit generation for affected SKU when source data exists.
  - Open incident if digest mismatch indicates possible tampering.
- Recovery check:
  - Artifact endpoint returns required fields and replay context for affected SKUs.

## Ownership and escalation

- Primary owner: backend on-call.
- Secondary owner: platform/infra on-call for DB/Redis incidents.
- Escalation:
  - Page primary on-call immediately for SLO breach.
  - Escalate to secondary owner after 15 minutes unresolved.
  - Notify security/compliance owner immediately for artifact integrity incidents.
