# FiscalBrain-BR Scraper

Standalone Perl service that polls regulatory sources and pushes shared `IngestionJob`
payloads directly to the Redis ingestion stream consumed by the Rust worker.

## Run locally

```bash
cp .env.example .env
cp scraper/.env.example scraper/.env
cd scraper
cpanm --installdeps .
prove -lr t/
REDIS_URL=redis://localhost:6379/0 perl bin/scraper.pl minion worker -j 4
```

## Notes

- Dedup state is stored in Redis only.
- Jobs are published to `queue_ingestion` as JSON under the `payload` stream field.
- Failed source runs are mirrored to `queue_ingestion_dlq`.
