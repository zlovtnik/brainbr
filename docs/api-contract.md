# API Contract (v1)

Base path: `/api/v1`

## Authentication

- Primary mechanism: `Authorization: Bearer <jwt>`.
- Phase 1 baseline: inventory routes use Bearer JWT only. API key support is deferred.
- JWT requirements:
  - Signed access token (`alg` enforced by gateway/middleware).
  - Must include `tenant_id` claim (mapped server-side to `company_id`), `user` claim, and `scope` or `role` claims.
  - Short-lived access token (recommended max 60 minutes) with refresh flow handled outside this API contract.

Example headers (`POST /inventory/sku`):

```http
Authorization: Bearer eyJhbGciOi...
Content-Type: application/json
X-Request-Id: 88f41524-c1e5-4220-9f71-f7d6ce7fdd58
```

## Authorization

- Authorization model: tenant-scoped RBAC + scopes.
- Tenant enforcement:
  - API never accepts client-supplied `company_id` as authoritative tenant context.
  - Tenant context is server-derived from verified auth/session context.
- Recommended scope mapping:
  - `inventory:write` for `POST /inventory/sku`, `PUT /inventory/sku/{sku_id}`, and `DELETE /inventory/sku/{sku_id}`.
  - `inventory:read` for `GET /inventory/sku` and `GET /inventory/sku/{sku_id}`.
  - `audit:trigger` for `POST /inventory/sku/{sku_id}/re-audit` (Phase 2).
  - `audit:read` for `GET /audit/explain/{sku_id}` and `GET /audit/law/{law_ref}`.
  - `audit:query` for `POST /audit/query`.

Forbidden response shape (HTTP `403`):

```json
{
  "error_code": "FORBIDDEN",
  "message": "Missing required scope inventory:write",
  "request_id": "trace-id"
}
```

## Rate Limiting

Per-tenant default limits:

- General endpoints: `30 req/min`, burst `60`.
- `POST /inventory/sku`: `5 req/min`, burst `10`.
- `POST /audit/query`: `2 req/min`, burst `5`.

Returned headers:

- `X-RateLimit-Limit`
- `X-RateLimit-Remaining`
- `Retry-After` (seconds when throttled)

Rate-limit exceeded response (`429`):

```json
{
  "error_code": "RATE_LIMIT_EXCEEDED",
  "message": "Rate limit exceeded for tenant",
  "request_id": "trace-id"
}
```

## CORS

- Allowed origins: explicit allowlist per environment (for example `https://app.fiscalbrain.com`, `https://staging.fiscalbrain.com`).
- Allowed methods: `GET, POST, PUT, PATCH, DELETE, OPTIONS`.
- Allowed headers: `Authorization, X-API-Key, Content-Type, X-Request-Id`.
- Credentials: disabled for browser cookie auth (`Access-Control-Allow-Credentials: false`) unless explicitly enabled in deployment policy.
- Preflight requests must return allowed methods/headers and max age.

Preflight example for browser call to `GET /inventory/impact`:

```http
OPTIONS /api/v1/inventory/impact?page=1&limit=50 HTTP/1.1
Origin: https://app.fiscalbrain.com
Access-Control-Request-Method: GET
Access-Control-Request-Headers: Authorization,Content-Type
```

```http
HTTP/1.1 204 No Content
Access-Control-Allow-Origin: https://app.fiscalbrain.com
Access-Control-Allow-Methods: GET,POST,OPTIONS
Access-Control-Allow-Headers: Authorization,Content-Type,X-Request-Id,X-API-Key
Access-Control-Max-Age: 600
```

## Inventory

### `POST /inventory/sku`

Creates or upserts tenant-scoped SKU metadata.

Request body:

```json
{
  "sku_id": "SKU-001",
  "description": "Cerveja Pilsen Lata 350ml",
  "ncm_code": "22030000",
  "origin_state": "SP",
  "destination_state": "RJ",
  "legacy_taxes": {
    "icms": 18.0,
    "pis": 1.65,
    "cofins": 7.6,
    "iss": 0.0
  }
}
```

`company_id` is not accepted from client payload/query/header; tenant is resolved server-side from authenticated context.

Responses:

- `200 OK`: upsert result (`created` or `updated`).
- `400 Bad Request`: payload invalid.

Response example:

```json
{
  "sku_id": "SKU-001",
  "status": "created"
}
```

### `GET /inventory/sku/{sku_id}`

Returns tenant-scoped SKU metadata and tax payload snapshots.

Request requirements:

- Path param: `sku_id` (string).
- Tenant scoping: server-derived from authentication context.
- Required header: `Authorization: Bearer <jwt>`.
- Optional query param: `include_inactive` (bool, default `false`).

Success response (`200`):

```json
{
  "sku_id": "SKU-001",
  "description": "Cerveja Pilsen Lata 350ml",
  "ncm_code": "22030000",
  "origin_state": "SP",
  "destination_state": "RJ",
  "legacy_taxes": {
    "icms": 18.0
  },
  "reform_taxes": {},
  "is_active": true,
  "updated_at": "2026-03-25T15:10:00Z"
}
```

Error responses:

- `400 Bad Request`: missing/invalid request requirements.
- `404 Not Found`: SKU not found in tenant scope.

### `GET /inventory/sku`

Lists tenant-scoped SKUs with pagination.

Query parameters:

- `page` (int, optional, default `1`, min `1`).
- `limit` (int, optional, default `50`, max `100`).
- `include_inactive` (bool, optional, default `false`).

Response (`200`):

```json
{
  "items": [
    {
      "sku_id": "SKU-001",
      "description": "Cerveja Pilsen Lata 350ml",
      "ncm_code": "22030000",
      "origin_state": "SP",
      "destination_state": "RJ",
      "legacy_taxes": {
        "icms": 18.0
      },
      "reform_taxes": {},
      "is_active": true,
      "updated_at": "2026-03-25T15:10:00Z"
    }
  ],
  "total_count": 1,
  "page": 1,
  "limit": 50,
  "has_more": false
}
```

### `PUT /inventory/sku/{sku_id}`

Updates a tenant-scoped SKU. Returns `404` when SKU is not present in current tenant.

Response (`200`):

```json
{
  "sku_id": "SKU-001",
  "status": "updated"
}
```

### `DELETE /inventory/sku/{sku_id}`

Soft deletes a tenant-scoped SKU (`is_active=false`).

Response (`200`):

```json
{
  "sku_id": "SKU-001",
  "status": "deleted"
}
```

Phase 1 note: `POST /inventory/sku/{sku_id}/re-audit` is deferred to Phase 2.

### `GET /inventory/impact`

Paginated fiscal impact data from materialized view.

Query parameters:

- `page` (int, optional, default `1`, min `1`).
- `limit` (int, optional, default `50`, max `100`).

Example request:

```http
GET /api/v1/inventory/impact?page=1&limit=50
```

Response (`200`):

```json
{
  "data": [
    {
      "sku_id": "SKU-001",
      "ncm_code": "22030000",
      "legacy_burden": 27.25,
      "reform_burden": 24.4,
      "delta": -2.85,
      "transition_risk_score": 4
    }
  ],
  "totalCount": 332,
  "page": 1,
  "limit": 50,
  "hasMore": true
}
```

Error responses:

- `400 Bad Request`: invalid pagination/filter params.
- `401 Unauthorized`: missing or invalid credentials.
- `403 Forbidden`: insufficient scope.
- `500 Internal Server Error`: unexpected failure.

### `GET /inventory/risk`

Lists SKUs by `transition_risk_score`.

Query parameters:

- Pagination:
  - `page` (int, default `1`, min `1`).
  - `limit` (int, default `50`, max `100`).
- Filtering:
  - `min_transition_risk_score` (int, optional).
  - `max_transition_risk_score` (int, optional).
  - `ncm_code` (string, optional).
  - `updated_after` (ISO date-time, optional).
- Sorting:
  - `sort_by` accepts `transition_risk_score`.
  - `sort_dir` accepts `asc` or `desc`.

Response schema (`200`):

```json
{
  "items": [
    {
      "sku_id": "SKU-001",
      "transition_risk_score": 8,
      "last_updated": "2026-03-25T15:10:00Z",
      "metadata": {
        "ncm_code": "22030000",
        "audit_confidence": 0.91,
        "llm_model_used": "gpt-4o"
      }
    }
  ],
  "total_count": 150,
  "page": 1,
  "limit": 50
}
```

Expected status codes:

- `200 OK`: successful response.
- `400 Bad Request`: invalid query parameters.
- `401 Unauthorized`: invalid/missing credentials.
- `403 Forbidden`: missing permission/scope.
- `404 Not Found`: resource unavailable.
- `500 Internal Server Error`: server error.

## Audit

### `GET /audit/explain/{sku_id}`

Returns explainability package for the SKU.

Response example (`200`):

```json
{
  "sku_id": "SKU-001",
  "reform_taxes": {
    "cbs": 8.8,
    "ibs": 17.5,
    "tax_rate": 26.3,
    "is_taxable": true
  },
  "audit_confidence": 0.93,
  "llm_model_used": "gpt-4o",
  "source": {
    "law_ref": "LC-68-2024-art-12",
    "content": "Texto legal relevante para o SKU...",
    "source_url": "https://www.planalto.gov.br/..."
  }
}
```

Expected status codes:

- `200 OK`
- `404 Not Found`
- `500 Internal Server Error`

### `GET /audit/law/{law_ref}`

Returns legal paragraph by reference (tenant override aware).

Tenant override behavior:

1. Attempt tenant-specific override lookup for current tenant and `law_ref`.
2. If no override exists, fall back to global/base law row.
3. Response field `override_source` identifies selected source (`tenant_override` or `global_default`).
4. If both candidates exist, tenant override always wins.

Response example (`200`):

```json
{
  "id": "22d2e90f-b037-48c7-af2f-df63a1577d2f",
  "ref": "LC-68-2024-art-12",
  "title": "Artigo 12 - CBS",
  "content": "Texto consolidado aplicado na auditoria.",
  "tenant_id": "00000000-0000-0000-0000-000000000001",
  "source": "planalto",
  "effective_at": "2027-01-01",
  "override_source": "tenant_override"
}
```

Expected status codes:

- `200 OK`
- `400 Bad Request`
- `404 Not Found`
- `500 Internal Server Error`

### `POST /audit/query`

Semantic query over law base with top-K retrieval.

Request body schema:

- `query` (string, required): user query text.
- `k` (int, optional): top-K retrieval size. Default `5`, maximum `20`.
- `filters` (object, optional): structured constraints (`law_type`, `published_after`, `tags`, etc.).

Request example:

```json
{
  "query": "Regras de IBS e CBS para bebidas",
  "k": 5,
  "filters": {
    "law_type": "complementary_law",
    "published_after": "2024-01-01"
  }
}
```

Response example (`200`):

```json
{
  "results": [
    {
      "id": "a5b3f8a3-5b5c-4ff5-bef7-e7cc1b918d51",
      "title": "LC 68/2024 - Artigo 12",
      "content": "Trecho legal relevante para a pergunta.",
      "metadata": {
        "law_ref": "LC-68-2024-art-12",
        "source_url": "https://www.planalto.gov.br/..."
      },
      "score": 0.8732
    }
  ]
}
```

Expected status codes:

- `200 OK`: query executed successfully.
- `400 Bad Request`: invalid input body.
- `422 Unprocessable Entity`: `k` out of range.
- `500 Internal Server Error`: unexpected server failure.

## Transition

### `GET /transition/calendar`

Returns 2026-2033 transition parameters as `years` list.

Response example (`200`):

```json
{
  "years": [
    {
      "year": 2026,
      "reform_weight": 0.1,
      "legacy_weight": 0.9
    },
    {
      "year": 2027,
      "reform_weight": 1.0,
      "legacy_weight": 0.0
    },
    {
      "year": 2028,
      "reform_weight": 1.0,
      "legacy_weight": 0.0
    },
    {
      "year": 2029,
      "reform_weight": 1.0,
      "legacy_weight": 0.0
    },
    {
      "year": 2030,
      "reform_weight": 1.0,
      "legacy_weight": 0.0
    },
    {
      "year": 2031,
      "reform_weight": 1.0,
      "legacy_weight": 0.0
    },
    {
      "year": 2032,
      "reform_weight": 1.0,
      "legacy_weight": 0.0
    },
    {
      "year": 2033,
      "reform_weight": 1.0,
      "legacy_weight": 0.0
    }
  ]
}
```

### `GET /transition/effective-rate/{sku_id}?year=YYYY`

Returns blended burden for selected year.

Year validation:

- Allowed range: `2026` through `2033`.
- Years outside range return `400 Bad Request`.

Response example (`200`):

```json
{
  "sku_id": "SKU-001",
  "year": 2029,
  "blended_burden": {
    "legacy_component": 12.42,
    "reform_component": 10.15,
    "total": 22.57,
    "currency": "BRL"
  }
}
```

Expected status codes:

- `200 OK`: successful response with blended burden.
- `400 Bad Request`: invalid year parameter.
- `404 Not Found`: `sku_id` not found.

## Error model

Use standard shape:

```json
{
  "error_code": "SKU_NOT_FOUND",
  "message": "SKU SKU-001 not found",
  "request_id": "trace-id"
}
```

## API invariants

- All endpoints are tenant-scoped.
- Tenant context (`company_id`) is server-derived and validated before data access.
- Write endpoints emit audit events when rates change.
- Tax payloads must validate against strict schema before persistence.
