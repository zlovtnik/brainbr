package br.fiscalbrain.audit

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

@Repository
class AuditRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    private val anyMapType = object : TypeReference<Map<String, Any>>() {}

    fun findSku(companyId: UUID, skuId: String): AuditSkuRecord? {
        val sql = """
            SELECT sku_id, company_id, description, ncm_code, origin_state, destination_state, legacy_taxes::text AS legacy_taxes
            FROM inventory_transition
            WHERE company_id = ?
              AND sku_id = ?
              AND is_active = TRUE
        """.trimIndent()

        return jdbcTemplate.query(sql, skuRowMapper(), companyId, skuId).firstOrNull()
    }

    fun persistAuditResult(
        companyId: UUID,
        skuId: String,
        reformTaxes: Map<String, Any>,
        vectorId: UUID,
        auditConfidence: Double,
        llmModelUsed: String
    ) {
        val sql = """
            UPDATE inventory_transition
            SET reform_taxes = ?::jsonb,
                vector_id = ?,
                audit_confidence = ?,
                llm_model_used = ?,
                last_llm_audit = NOW(),
                updated_at = NOW()
            WHERE company_id = ?
              AND sku_id = ?
              AND is_active = TRUE
        """.trimIndent()

        val updated = jdbcTemplate.update(
            sql,
            objectMapper.writeValueAsString(reformTaxes),
            vectorId,
            auditConfidence,
            llmModelUsed,
            companyId,
            skuId
        )

        if (updated == 0) {
            throw AuditNotFoundException("SKU $skuId not found")
        }
    }

    fun updateRiskScore(companyId: UUID, skuId: String, riskScore: Int) {
        val sql = """
            UPDATE inventory_transition
            SET transition_risk_score = ?,
                updated_at = NOW()
            WHERE company_id = ?
              AND sku_id = ?
              AND is_active = TRUE
        """.trimIndent()

        val updated = jdbcTemplate.update(sql, riskScore, companyId, skuId)
        if (updated == 0) {
            throw AuditNotFoundException("SKU $skuId not found for company $companyId")
        }
    }

    fun appendAuditEvent(
        companyId: UUID,
        skuId: String,
        eventType: String,
        actor: String?,
        requestId: String?,
        payload: Map<String, Any>,
        runId: UUID? = null,
        artifactVersion: String? = null,
        artifactDigest: String? = null
    ) {
        val sql = """
            INSERT INTO fiscal_audit_log (
                company_id, sku_id, event_type, actor, request_id, event_payload, run_id, artifact_version, artifact_digest
            ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
        """.trimIndent()

        jdbcTemplate.update(
            sql,
            companyId,
            skuId,
            eventType,
            actor,
            requestId,
            objectMapper.writeValueAsString(payload),
            runId,
            artifactVersion,
            artifactDigest
        )
    }

    fun persistExplainabilityRun(
        companyId: UUID,
        skuId: String,
        jobId: String,
        requestId: String?,
        artifactVersion: String,
        schemaVersion: String,
        llmModelUsed: String,
        vectorId: UUID,
        auditConfidence: Double,
        sourceSnapshot: Map<String, Any>,
        replayContext: Map<String, Any>,
        ragOutput: Map<String, Any>,
        artifactDigest: String
    ): ExplainabilityRunRecord {
        val sql = """
            INSERT INTO audit_explainability_run (
                company_id, sku_id, job_id, request_id, artifact_version, schema_version, llm_model_used, vector_id,
                audit_confidence, source_snapshot, replay_context, rag_output, artifact_digest
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?)
            RETURNING id, company_id, sku_id, job_id, request_id, artifact_version, schema_version, llm_model_used,
                      vector_id, audit_confidence, source_snapshot::text AS source_snapshot_json,
                      replay_context::text AS replay_context_json, rag_output::text AS rag_output_json,
                      artifact_digest, created_at
        """.trimIndent()

        return jdbcTemplate.queryForObject(
            sql,
            explainabilityRunRowMapper(),
            companyId,
            skuId,
            jobId,
            requestId,
            artifactVersion,
            schemaVersion,
            llmModelUsed,
            vectorId,
            auditConfidence,
            objectMapper.writeValueAsString(sourceSnapshot),
            objectMapper.writeValueAsString(replayContext),
            objectMapper.writeValueAsString(ragOutput),
            artifactDigest
        ) ?: throw IllegalStateException("Failed to persist explainability run for sku_id=$skuId")
    }

    fun findLatestExplainabilityRun(companyId: UUID, skuId: String): ExplainabilityRunRecord? {
        val sql = """
            SELECT id, company_id, sku_id, job_id, request_id, artifact_version, schema_version, llm_model_used,
                   vector_id, audit_confidence, source_snapshot::text AS source_snapshot_json,
                   replay_context::text AS replay_context_json, rag_output::text AS rag_output_json,
                   artifact_digest, created_at
            FROM audit_explainability_run
            WHERE company_id = ?
              AND sku_id = ?
            ORDER BY created_at DESC
            LIMIT 1
        """.trimIndent()

        return jdbcTemplate.query(sql, explainabilityRunRowMapper(), companyId, skuId).firstOrNull()
    }

    fun findExplainabilityRun(companyId: UUID, runId: UUID): ExplainabilityRunRecord? {
        val sql = """
            SELECT id, company_id, sku_id, job_id, request_id, artifact_version, schema_version, llm_model_used,
                   vector_id, audit_confidence, source_snapshot::text AS source_snapshot_json,
                   replay_context::text AS replay_context_json, rag_output::text AS rag_output_json,
                   artifact_digest, created_at
            FROM audit_explainability_run
            WHERE company_id = ?
              AND id = ?
            LIMIT 1
        """.trimIndent()

        return jdbcTemplate.query(sql, explainabilityRunRowMapper(), companyId, runId).firstOrNull()
    }

    fun findExplain(companyId: UUID, skuId: String): AuditExplainRecord? {
        val sql = """
            SELECT i.sku_id,
                   i.reform_taxes::text AS reform_taxes,
                   i.audit_confidence,
                   i.llm_model_used,
                   c.id AS chunk_id,
                   c.content AS chunk_content,
                   b.law_ref,
                   COALESCE(b.source_url, '') AS source_url
            FROM inventory_transition i
            LEFT JOIN fiscal_knowledge_chunk c ON c.id = i.vector_id
            LEFT JOIN fiscal_knowledge_base b ON b.id = c.knowledge_id
            WHERE i.company_id = ?
              AND i.sku_id = ?
              AND i.is_active = TRUE
        """.trimIndent()

        return jdbcTemplate.query(sql, explainRowMapper(), companyId, skuId).firstOrNull()
    }

    private fun skuRowMapper(): RowMapper<AuditSkuRecord> = RowMapper { rs: ResultSet, _: Int ->
        val skuId = rs.getString("sku_id")
            ?: throw SQLException("Column sku_id is null while mapping AuditSkuRecord")
        val companyId = rs.getObject("company_id", UUID::class.java)
            ?: throw SQLException("Column company_id is null while mapping AuditSkuRecord for sku_id=$skuId")
        val description = rs.getString("description")
            ?: throw SQLException("Column description is null while mapping AuditSkuRecord for sku_id=$skuId")
        val ncmCode = rs.getString("ncm_code")
            ?: throw SQLException("Column ncm_code is null while mapping AuditSkuRecord for sku_id=$skuId")
        val originState = rs.getString("origin_state")
            ?: throw SQLException("Column origin_state is null while mapping AuditSkuRecord for sku_id=$skuId")
        val destinationState = rs.getString("destination_state")
            ?: throw SQLException("Column destination_state is null while mapping AuditSkuRecord for sku_id=$skuId")
        val legacyTaxesRaw = rs.getString("legacy_taxes")
            ?: throw SQLException("Column legacy_taxes is null while mapping AuditSkuRecord for sku_id=$skuId")
        val legacyTaxes = try {
            objectMapper.readValue(legacyTaxesRaw, anyMapType)
        } catch (ex: JsonProcessingException) {
            throw SQLException(
                "Malformed legacy_taxes JSON while mapping AuditSkuRecord for sku_id=$skuId company_id=$companyId",
                ex
            )
        }

        AuditSkuRecord(
            skuId = skuId,
            companyId = companyId,
            description = description,
            ncmCode = ncmCode,
            originState = originState,
            destinationState = destinationState,
            legacyTaxes = legacyTaxes
        )
    }

    private fun explainRowMapper(): RowMapper<AuditExplainRecord> = RowMapper { rs: ResultSet, _: Int ->
        val context = "AuditExplainRecord"
        val skuId = requireString(rs, "sku_id", context)
        val reformTaxesJson = rs.getString("reform_taxes")
        val reformTaxes = if (reformTaxesJson.isNullOrBlank()) {
            emptyMap()
        } else {
            try {
                objectMapper.readValue(reformTaxesJson, anyMapType)
            } catch (ex: JsonProcessingException) {
                throw SQLException(
                    "Malformed reform_taxes JSON while mapping $context for sku_id=$skuId",
                    ex
                )
            }
        }

        AuditExplainRecord(
            skuId = skuId,
            reformTaxes = reformTaxes,
            auditConfidence = requireDouble(rs, "audit_confidence", context),
            llmModelUsed = rs.getString("llm_model_used") ?: "",
            chunkId = rs.getObject("chunk_id", UUID::class.java),
            sourceContent = rs.getString("chunk_content") ?: "",
            lawRef = rs.getString("law_ref") ?: "",
            sourceUrl = rs.getString("source_url") ?: ""
        )
    }

    private fun explainabilityRunRowMapper(): RowMapper<ExplainabilityRunRecord> = RowMapper { rs: ResultSet, _: Int ->
        val context = "ExplainabilityRunRecord"
        ExplainabilityRunRecord(
            id = requireUuid(rs, "id", context),
            companyId = requireUuid(rs, "company_id", context),
            skuId = requireString(rs, "sku_id", context),
            jobId = requireString(rs, "job_id", context),
            requestId = rs.getString("request_id"),
            artifactVersion = requireString(rs, "artifact_version", context),
            schemaVersion = requireString(rs, "schema_version", context),
            llmModelUsed = requireString(rs, "llm_model_used", context),
            vectorId = requireUuid(rs, "vector_id", context),
            auditConfidence = requireDouble(rs, "audit_confidence", context),
            sourceSnapshot = readAnyMap(
                raw = requireString(rs, "source_snapshot_json", context),
                fieldName = "source_snapshot_json",
                context = context
            ),
            replayContext = readAnyMap(
                raw = requireString(rs, "replay_context_json", context),
                fieldName = "replay_context_json",
                context = context
            ),
            ragOutput = readAnyMap(
                raw = requireString(rs, "rag_output_json", context),
                fieldName = "rag_output_json",
                context = context
            ),
            artifactDigest = requireString(rs, "artifact_digest", context),
            createdAt = requireTimestampInstant(rs, "created_at", context)
        )
    }

    private fun readAnyMap(raw: String, fieldName: String, context: String): Map<String, Any> {
        if (raw.isBlank()) {
            return emptyMap()
        }
        return try {
            objectMapper.readValue(raw, anyMapType)
        } catch (ex: JsonProcessingException) {
            val snippet = raw.take(240)
            throw SQLException(
                "Malformed JSON in column '$fieldName' while mapping $context (raw_snippet=$snippet)",
                ex
            )
        }
    }

    private fun requireUuid(rs: ResultSet, column: String, context: String): UUID {
        return rs.getObject(column, UUID::class.java)
            ?: throw SQLException("Missing required column '$column' for $context")
    }

    private fun requireString(rs: ResultSet, column: String, context: String): String {
        return rs.getString(column)
            ?: throw SQLException("Missing required column '$column' for $context")
    }

    private fun requireDouble(rs: ResultSet, column: String, context: String): Double {
        val value = rs.getDouble(column)
        if (rs.wasNull()) {
            throw SQLException("Missing required column '$column' for $context")
        }
        return value
    }

    private fun requireTimestampInstant(rs: ResultSet, column: String, context: String): Instant {
        val timestamp = rs.getTimestamp(column)
            ?: throw SQLException("Missing required column '$column' for $context")
        return timestamp.toInstant()
    }
}

data class AuditSkuRecord(
    val skuId: String,
    val companyId: UUID,
    val description: String,
    val ncmCode: String,
    val originState: String,
    val destinationState: String,
    val legacyTaxes: Map<String, Any>
)

data class AuditExplainRecord(
    val skuId: String,
    val reformTaxes: Map<String, Any>,
    val auditConfidence: Double,
    val llmModelUsed: String,
    val chunkId: UUID?,
    val sourceContent: String,
    val lawRef: String,
    val sourceUrl: String
)

data class ExplainabilityRunRecord(
    val id: UUID,
    val companyId: UUID,
    val skuId: String,
    val jobId: String,
    val requestId: String?,
    val artifactVersion: String,
    val schemaVersion: String,
    val llmModelUsed: String,
    val vectorId: UUID,
    val auditConfidence: Double,
    val sourceSnapshot: Map<String, Any>,
    val replayContext: Map<String, Any>,
    val ragOutput: Map<String, Any>,
    val artifactDigest: String,
    val createdAt: Instant
)

class AuditNotFoundException(message: String) : RuntimeException(message)
class AuditProcessingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

fun auditEventPayload(
    skuId: String,
    vectorId: UUID,
    auditConfidence: Double,
    llmModelUsed: String,
    runId: UUID? = null,
    artifactVersion: String? = null,
    artifactDigest: String? = null,
    eventAt: Instant = Instant.now()
): Map<String, Any> {
    val payload = mutableMapOf<String, Any>(
        "sku_id" to skuId,
        "vector_id" to vectorId.toString(),
        "audit_confidence" to auditConfidence,
        "llm_model_used" to llmModelUsed,
        "event_at" to eventAt.toString()
    )
    if (runId != null) {
        payload["run_id"] = runId.toString()
    }
    if (!artifactVersion.isNullOrBlank()) {
        payload["artifact_version"] = artifactVersion
    }
    if (!artifactDigest.isNullOrBlank()) {
        payload["artifact_digest"] = artifactDigest
    }
    return payload
}
