CREATE TABLE IF NOT EXISTS schema_migration_baseline (
    id            SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    description   TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO schema_migration_baseline (id, description)
VALUES (1, 'FiscalBrain-BR baseline migration')
ON CONFLICT (id) DO NOTHING;

