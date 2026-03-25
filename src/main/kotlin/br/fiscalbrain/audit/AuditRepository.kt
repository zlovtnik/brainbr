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

    fun appendAuditEvent(
        companyId: UUID,
        skuId: String,
        eventType: String,
        actor: String?,
        requestId: String?,
        payload: Map<String, Any>
    ) {
        val sql = """
            INSERT INTO fiscal_audit_log (
                company_id, sku_id, event_type, actor, request_id, event_payload
            ) VALUES (?, ?, ?, ?, ?, ?::jsonb)
        """.trimIndent()

        jdbcTemplate.update(
            sql,
            companyId,
            skuId,
            eventType,
            actor,
            requestId,
            objectMapper.writeValueAsString(payload)
        )
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
        val reformTaxesJson = rs.getString("reform_taxes")
        AuditExplainRecord(
            skuId = rs.getString("sku_id"),
            reformTaxes = if (reformTaxesJson.isNullOrBlank()) emptyMap() else objectMapper.readValue(reformTaxesJson, anyMapType),
            auditConfidence = rs.getDouble("audit_confidence"),
            llmModelUsed = rs.getString("llm_model_used") ?: "",
            chunkId = rs.getObject("chunk_id") as UUID?,
            sourceContent = rs.getString("chunk_content") ?: "",
            lawRef = rs.getString("law_ref") ?: "",
            sourceUrl = rs.getString("source_url") ?: ""
        )
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

class AuditNotFoundException(message: String) : RuntimeException(message)
class AuditProcessingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

fun auditEventPayload(
    skuId: String,
    vectorId: UUID,
    auditConfidence: Double,
    llmModelUsed: String,
    eventAt: Instant = Instant.now()
): Map<String, Any> = mapOf(
    "sku_id" to skuId,
    "vector_id" to vectorId.toString(),
    "audit_confidence" to auditConfidence,
    "llm_model_used" to llmModelUsed,
    "event_at" to eventAt.toString()
)
