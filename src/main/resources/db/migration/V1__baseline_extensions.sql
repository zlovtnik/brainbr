-- Enable required extensions and defaults
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Ensure timestamps use UTC
SET TIMEZONE='UTC';
