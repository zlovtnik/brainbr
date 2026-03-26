package br.fiscalbrain.pipeline

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID

@Repository
class KnowledgeRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    private val mapTypeRef = object : TypeReference<Map<String, Any>>() {}

    fun upsertVersionedKnowledge(
        companyId: UUID,
        lawRef: String,
        lawType: String,
        content: String,
        sourceUrl: String?,
        publishedAt: LocalDate?,
        effectiveAt: LocalDate?,
        metadata: Map<String, Any>,
        contentHash: String
    ): KnowledgeDocument {
        val existing = findActiveKnowledgeDocument(companyId, lawRef)
        if (existing != null && existing.contentHash == contentHash) {
            return existing
        }

        val newVersion = (existing?.contentVersion ?: 0) + 1
        val inserted = insertKnowledgeDocument(
            companyId = companyId,
            lawRef = lawRef,
            lawType = lawType,
            content = content,
            sourceUrl = sourceUrl,
            publishedAt = publishedAt,
            effectiveAt = effectiveAt,
            metadata = metadata,
            contentHash = contentHash,
            contentVersion = newVersion
        )

        if (existing != null) {
            jdbcTemplate.update(
                """
                UPDATE fiscal_knowledge_base
                SET is_superseded = TRUE,
                    superseded_by = ?,
                    superseded_at = NOW(),
                    updated_at = NOW()
                WHERE id = ? AND company_id = ?
                """.trimIndent(),
                inserted.id,
                existing.id,
                companyId
            )
        }

        return inserted
    }

    private fun insertKnowledgeDocument(
        companyId: UUID,
        lawRef: String,
        lawType: String,
        content: String,
        sourceUrl: String?,
        publishedAt: LocalDate?,
        effectiveAt: LocalDate?,
        metadata: Map<String, Any>,
        contentHash: String,
        contentVersion: Int
    ): KnowledgeDocument {
        val sql = """
            INSERT INTO fiscal_knowledge_base (
                company_id, law_ref, law_type, content, metadata, source_url, published_at, effective_at, content_hash, content_version, is_superseded
            ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, FALSE)
            RETURNING id, company_id, law_ref, law_type, content, source_url, content_hash, content_version
        """.trimIndent()

        val metadataJson = objectMapper.writeValueAsString(metadata)

        return jdbcTemplate.queryForObject(
            sql,
            knowledgeDocumentRowMapper(),
            companyId,
            lawRef,
            lawType,
            content,
            metadataJson,
            sourceUrl,
            publishedAt,
            effectiveAt,
            contentHash,
            contentVersion
        ) ?: throw IllegalStateException("Failed to upsert fiscal knowledge document")
    }

    fun findKnowledgeDocument(companyId: UUID, lawRef: String): KnowledgeDocument? {
        val sql = """
            SELECT id, company_id, law_ref, law_type, content, source_url, content_hash, content_version
            FROM fiscal_knowledge_base
            WHERE company_id = ?
              AND law_ref = ?
            ORDER BY created_at DESC
            LIMIT 1
        """.trimIndent()
        return jdbcTemplate.query(sql, knowledgeDocumentRowMapper(), companyId, lawRef).firstOrNull()
    }

    fun findActiveKnowledgeDocument(companyId: UUID, lawRef: String): KnowledgeDocument? {
        val sql = """
            SELECT id, company_id, law_ref, law_type, content, source_url, content_hash, content_version
            FROM fiscal_knowledge_base
            WHERE company_id = ?
              AND law_ref = ?
              AND is_superseded = FALSE
        """.trimIndent()
        return jdbcTemplate.query(sql, knowledgeDocumentRowMapper(), companyId, lawRef).firstOrNull()
    }

    @Transactional
    fun replaceChunks(knowledgeId: UUID, companyId: UUID, chunks: List<ChunkWriteInput>) {
        jdbcTemplate.update(
            "DELETE FROM fiscal_knowledge_chunk WHERE knowledge_id = ? AND company_id = ?",
            knowledgeId,
            companyId
        )

        val sql = """
            INSERT INTO fiscal_knowledge_chunk (
                knowledge_id, company_id, chunk_index, content, embedding, metadata
            ) VALUES (?, ?, ?, ?, ?::vector, ?::jsonb)
        """.trimIndent()

        jdbcTemplate.batchUpdate(sql, chunks, chunks.size) { ps, chunk ->
            val metadataJson = try {
                objectMapper.writeValueAsString(chunk.metadata)
            } catch (ex: Exception) {
                throw IllegalStateException("Failed to serialize chunk metadata for chunk_index=${chunk.chunkIndex}", ex)
            }
            ps.setObject(1, knowledgeId)
            ps.setObject(2, companyId)
            ps.setInt(3, chunk.chunkIndex)
            ps.setString(4, chunk.content)
            ps.setString(5, VectorUtils.toVectorLiteral(chunk.embedding))
            ps.setString(6, metadataJson)
        }
    }

    fun queryChunks(
        companyId: UUID,
        queryEmbedding: List<Double>,
        topK: Int,
        lawType: String?,
        publishedAfter: LocalDate?
    ): List<KnowledgeChunk> {
        val queryVector = VectorUtils.toVectorLiteral(queryEmbedding)
        val args = mutableListOf<Any>(queryVector, companyId)

        val sql = StringBuilder(
            """
            SELECT c.id, c.knowledge_id, c.company_id, c.chunk_index, c.content,
                   c.metadata::text AS metadata_json,
                   (c.embedding <-> ?::vector) AS distance
            FROM fiscal_knowledge_chunk c
            INNER JOIN fiscal_knowledge_base b ON b.id = c.knowledge_id
            WHERE c.company_id = ?
              AND c.embedding IS NOT NULL
              AND b.is_superseded = FALSE
            """.trimIndent()
        )

        if (!lawType.isNullOrBlank()) {
            sql.append(" AND b.law_type = ?")
            args.add(lawType)
        }

        if (publishedAfter != null) {
            sql.append(" AND b.published_at >= ?")
            args.add(publishedAfter)
        }

        sql.append(" ORDER BY c.embedding <-> ?::vector LIMIT ?")
        args.add(queryVector)
        args.add(topK)

        return jdbcTemplate.query(sql.toString(), chunkRowMapper(), *args.toTypedArray())
    }

    fun findChunkSource(chunkId: UUID, companyId: UUID): ChunkSourceRecord? {
        val sql = """
            SELECT c.id,
                   b.law_ref,
                   c.content,
                   COALESCE(b.source_url, '') AS source_url
            FROM fiscal_knowledge_chunk c
            INNER JOIN fiscal_knowledge_base b ON b.id = c.knowledge_id
            WHERE c.id = ?
              AND c.company_id = ?
        """.trimIndent()

        return jdbcTemplate.query(sql, chunkSourceRowMapper(), chunkId, companyId).firstOrNull()
    }

    private fun knowledgeDocumentRowMapper(): RowMapper<KnowledgeDocument> = RowMapper { rs: ResultSet, _: Int ->
        KnowledgeDocument(
            id = rs.getObject("id", UUID::class.java),
            companyId = rs.getObject("company_id", UUID::class.java),
            lawRef = rs.getString("law_ref"),
            lawType = rs.getString("law_type"),
            content = rs.getString("content"),
            sourceUrl = rs.getString("source_url"),
            contentHash = rs.getString("content_hash"),
            contentVersion = rs.getInt("content_version")
        )
    }

    private fun chunkRowMapper(): RowMapper<KnowledgeChunk> = RowMapper { rs: ResultSet, _: Int ->
        val distance = rs.getDouble("distance")
        val metadata = readJsonMap(rs.getString("metadata_json"))
        KnowledgeChunk(
            id = rs.getObject("id", UUID::class.java),
            knowledgeId = rs.getObject("knowledge_id", UUID::class.java),
            companyId = rs.getObject("company_id", UUID::class.java),
            chunkIndex = rs.getInt("chunk_index"),
            content = rs.getString("content"),
            score = 1.0 / (1.0 + distance.coerceAtLeast(0.0)),
            metadata = metadata
        )
    }

    private fun chunkSourceRowMapper(): RowMapper<ChunkSourceRecord> = RowMapper { rs: ResultSet, _: Int ->
        ChunkSourceRecord(
            chunkId = rs.getObject("id", UUID::class.java),
            lawRef = rs.getString("law_ref"),
            content = rs.getString("content"),
            sourceUrl = rs.getString("source_url")
        )
    }

    private fun readJsonMap(raw: String?): Map<String, Any> {
        if (raw.isNullOrBlank()) {
            return emptyMap()
        }
        return objectMapper.readValue(raw, mapTypeRef)
    }
}

data class ChunkSourceRecord(
    val chunkId: UUID,
    val lawRef: String,
    val content: String,
    val sourceUrl: String
)
