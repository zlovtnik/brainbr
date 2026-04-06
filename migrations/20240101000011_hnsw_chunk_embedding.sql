-- HNSW index for approximate nearest-neighbour cosine search on chunk embeddings.
-- m=16 / ef_construction=64 are safe defaults for 1536-dim vectors at this scale.
CREATE INDEX IF NOT EXISTS idx_fiscal_knowledge_chunk_embedding_hnsw
    ON fiscal_knowledge_chunk
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- Partial index: only index rows that already have an embedding (avoids nulls in ANN path).
-- The main index above covers all rows; this comment documents intent.
-- Maintenance: run VACUUM ANALYZE fiscal_knowledge_chunk after bulk ingestion.
