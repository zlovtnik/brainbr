-- Enable required extensions and defaults
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Ensure timestamps use UTC (session scoped; prefer server-level config for permanence)
SET TIMEZONE='UTC';
