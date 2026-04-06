# DevSpec — Regulatory Scraper Service (`scraper`)

**Status:** Draft  
**Scope:** New standalone microservice  
**Language:** Perl 5.38+ (Mojolicious)  
**Integrates with:** FiscalBrain-BR ingestion pipeline via Redis stream `queue_ingestion`

---

## 1. Purpose

The scraper is a headless, scheduled microservice that monitors Brazilian regulatory
sources (CONFAZ, Receita Federal, Diário Oficial da União, and state DOEs), detects
new or amended legislation, and automatically enqueues `IngestionJob` payloads onto
the existing Redis ingestion stream — eliminating the need for manual form submission
through the UI.

It does **not** call the FiscalBrain REST API. It writes directly to Redis using the
same stream/group contract the Rust worker already consumes.

---

## 2. Boundaries

| In scope | Out of scope |
|---|---|
| Fetching and parsing regulatory HTML/PDF sources | RAG audit, embedding, vector upsert |
| Deduplication against already-ingested `law_ref` values | Tenant-specific ingestion (scraper produces global/shared rows only, `company_id = NULL`) |
| Enqueuing `IngestionJob` JSON onto `queue_ingestion` Redis stream | UI changes beyond removing the manual queue form |
| Dead-letter handling for fetch failures | Modifying the Rust worker or API |
| Structured logging with `request_id` and `source` correlation | |

---

## 3. Sources (v1)

| ID | Source | URL pattern | Format | Cadence |
|---|---|---|---|---|
| `confaz` | CONFAZ Convênios ICMS | `https://www.confaz.fazenda.gov.br/legislacao/convenios/` | HTML index + linked HTML | Every 6 h |
| `dou` | Diário Oficial da União (INPI/RFB sections) | `https://www.in.gov.br/servicos/busca-de-publicacoes` (API) | JSON | Every 12 h |
| `rfb_instrucoes` | Receita Federal — Instruções Normativas | `https://normas.receita.fazenda.gov.br/sijut2consulta/` | HTML | Every 24 h |

Additional sources are added by extending the `sources.yaml` config file — no code
changes required.

---

## 4. Runtime model

```
┌─────────────────────────────────────────────────────┐
│  scraper (Perl / Mojolicious::Lite + Minion)        │
│                                                     │
│  Scheduler (cron via Minion::Backend::Redis)        │
│    └─ per-source fetch job                          │
│         ├─ HTTP fetch (Mojo::UserAgent)             │
│         ├─ parse (Mojo::DOM / CAM::PDF::Utils)      │
│         ├─ dedup check (Redis SET per source)       │
│         ├─ normalise → IngestionJob JSON            │
│         └─ XADD queue_ingestion * payload <json>   │
│                                                     │
│  Dead-letter: failed jobs → queue_ingestion_dlq     │
└─────────────────────────────────────────────────────┘
         │ Redis streams (shared with Rust worker)
         ▼
┌─────────────────────────────────────────────────────┐
│  FiscalBrain-BR worker (Rust)                       │
│  Consumes queue_ingestion — no changes needed       │
└─────────────────────────────────────────────────────┘
```

The scraper runs as a fifth Docker Compose service (`scraper`). It shares the same
Redis instance. It has **no** database connection — deduplication state lives in Redis.

---

## 5. IngestionJob payload contract

The scraper must produce JSON that deserialises cleanly into the Rust `IngestionJob`
struct. Required fields:

```json
{
  "job_id":     "<uuidv4>",
  "company_id": "00000000-0000-0000-0000-000000000000",
  "law_ref":    "Convênio ICMS 42/2026",
  "law_type":   "convenio",
  "source_url": "https://www.confaz.fazenda.gov.br/...",
  "raw_content": "<full extracted text>",
  "published_at": "2026-04-01",
  "effective_at": null,
  "tags":       ["icms", "confaz"],
  "state":      null,
  "ncm_scope":  [],
  "request_id": "scraper-confaz-<job_id>",
  "attempt":    0,
  "created_at": "2026-04-01T00:00:00Z"
}
```

Rules:
- `company_id` is always the nil UUID (`00000000-…`) — scraper only produces shared/global knowledge rows.
- `law_ref` must be unique per source; the scraper derives it from the document title using a normalisation function (strip accents, collapse whitespace, uppercase).
- `raw_content` is the full plain-text extraction. PDFs are converted via `pdftotext` (poppler). HTML is stripped with `Mojo::DOM`.
- `published_at` is parsed from the document header or filename; `null` if not determinable.
- `request_id` follows the pattern `scraper-<source_id>-<job_id>` for traceability.

---

## 6. Deduplication

Before enqueuing, the scraper checks a Redis SET key per source:

```
KEY   scraper:seen:<source_id>
TYPE  Redis SET
TTL   90 days (rolling — refreshed on each successful enqueue)
```

The member stored is `SHA-256(law_ref + raw_content)`. If the hash is already a member,
the document is skipped. This prevents re-ingestion of unchanged legislation without
requiring a DB connection.

On content change (same `law_ref`, different hash), the old hash is removed and the new
one added — the Rust worker's `ON CONFLICT … DO UPDATE` path handles the upsert.

---

## 7. Configuration (`scraper/.env` / environment)

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `REDIS_URL` | yes | — | Shared Redis instance (`redis://redis:6379`) |
| `INGESTION_STREAM` | no | `queue_ingestion` | Target Redis stream name |
| `INGESTION_DLQ` | no | `queue_ingestion_dlq` | Dead-letter stream |
| `SCRAPER_SOURCES_FILE` | no | `config/sources.yaml` | Source definitions |
| `SCRAPER_USER_AGENT` | no | `FiscalBrain-Scraper/1.0` | HTTP User-Agent header |
| `SCRAPER_REQUEST_TIMEOUT_S` | no | `30` | Per-request timeout in seconds |
| `SCRAPER_MAX_RETRIES` | no | `3` | Fetch retries before DLQ |
| `LOG_LEVEL` | no | `info` | `debug` / `info` / `warn` / `error` |

All values are read from environment — no hardcoded secrets.

---

## 8. Error handling

| Failure | Behaviour |
|---|---|
| HTTP 4xx on source fetch | Log warning, skip document, do not DLQ |
| HTTP 5xx / timeout | Retry up to `SCRAPER_MAX_RETRIES` with exponential backoff (base 5 s) |
| PDF parse failure | Log error with `source_url`, write raw error payload to DLQ, continue |
| Redis write failure | Log error, retry once; if still failing, log critical and exit job (Minion will reschedule) |
| Malformed document (no `law_ref` derivable) | Log warning with URL, skip |

All errors include `source_id`, `source_url`, and a `request_id` in the log line.

---

## 9. Project layout

```
scraper/
  bin/
    scraper.pl          # Mojolicious::Lite app + Minion worker entrypoint
  lib/
    Scraper/
      Source/
        Confaz.pm       # CONFAZ-specific fetch + parse logic
        Dou.pm          # DOU API adapter
        RfbInstrucoes.pm
      Dedup.pm          # Redis SET dedup logic
      Normalise.pm      # law_ref normalisation, text cleaning
      Publisher.pm      # XADD to Redis stream
      Job.pm            # IngestionJob struct builder + JSON serialisation
  config/
    sources.yaml        # Source definitions (URL, cadence, parser class, tags)
  t/
    normalise.t         # Unit tests for law_ref normalisation
    dedup.t             # Unit tests for dedup logic
    job.t               # IngestionJob JSON schema validation
    confaz.t            # Integration test against fixture HTML
  cpanfile            # Declared dependencies
  Dockerfile
  .env.example
```

---

## 10. Dependencies (`cpanfile`)

```perl
requires 'Mojolicious',          '9.35';
requires 'Mojo::Redis',          '3.29';   # Redis client (Mojo-native)
requires 'Minion',               '10.30';  # Job queue / scheduler
requires 'Minion::Backend::Redis','0.04';  # Minion backend on Redis
requires 'YAML::XS',             '0.89';   # sources.yaml parsing
requires 'Digest::SHA',          '6.04';   # SHA-256 for dedup hashing
requires 'UUID::Tiny',           '1.04';   # UUIDv4 generation
requires 'DateTime::Format::ISO8601', '0.16'; # date parsing
requires 'Text::Unidecode',      '1.30';   # accent stripping for law_ref
requires 'IPC::Run3',            '0.049';  # pdftotext subprocess
```

---

## 11. Dockerfile

```dockerfile
FROM perl:5.38-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    poppler-utils \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY cpanfile .
RUN cpanm --notest --installdeps .

COPY . .

CMD ["perl", "bin/scraper.pl", "minion", "worker", "-j", "4"]
```

---

## 12. Docker Compose addition

```yaml
scraper:
  build: ./scraper
  restart: unless-stopped
  depends_on:
    - redis
  environment:
    REDIS_URL: redis://redis:6379
    INGESTION_STREAM: queue_ingestion
    INGESTION_DLQ: queue_ingestion_dlq
    LOG_LEVEL: info
  env_file:
    - .env
```

No DB credentials. No `api` dependency.

---

## 13. Acceptance criteria

- [ ] `docker compose up scraper` starts without error.
- [ ] CONFAZ source fetches at least one document from the live index and enqueues a valid `IngestionJob` JSON onto `queue_ingestion`.
- [ ] Re-running the same source within the TTL window produces zero new enqueues (dedup working).
- [ ] Modifying a document's content (different hash) produces exactly one new enqueue.
- [ ] A simulated HTTP 5xx retries up to `SCRAPER_MAX_RETRIES` then writes to DLQ.
- [ ] All unit tests in `t/` pass under `prove -r t/`.
- [ ] `law_ref` normalisation is deterministic: same input always produces same output.
- [ ] `request_id` in every enqueued job follows `scraper-<source_id>-<job_id>` pattern.
- [ ] No DB credentials or API tokens appear in scraper config or logs.

---

## 14. Out of scope for v1 / future work

- State-specific DOE scraping (27 states) — add as new source entries in `sources.yaml`.
- Authenticated sources (e.g. SEFAZ portals behind login) — requires a separate credential-vault integration.
- PDF OCR for scanned documents — `pdftotext` handles digital PDFs only; OCR is a Phase 4 item.
- Tenant-scoped scraping (company-specific regulatory watches) — requires API integration and is a separate capability.
- UI for scraper job status / last-run timestamps — tracked in backlog.
