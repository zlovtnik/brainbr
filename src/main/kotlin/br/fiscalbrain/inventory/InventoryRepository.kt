package br.fiscalbrain.inventory

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class InventoryRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    private val mapTypeRef = object : TypeReference<Map<String, Double>>() {}

    fun upsert(record: InventoryWriteRequest, companyId: UUID): Boolean {
        val sql = """
            INSERT INTO inventory_transition (
                sku_id, company_id, description, ncm_code, origin_state, destination_state, legacy_taxes, is_active
            )
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, TRUE)
            ON CONFLICT (sku_id, company_id) DO UPDATE SET
                description = EXCLUDED.description,
                ncm_code = EXCLUDED.ncm_code,
                origin_state = EXCLUDED.origin_state,
                destination_state = EXCLUDED.destination_state,
                legacy_taxes = EXCLUDED.legacy_taxes,
                is_active = TRUE,
                updated_at = NOW()
            RETURNING xmax = 0
        """.trimIndent()
        val payload = objectMapper.writeValueAsString(record.legacyTaxes)
        return jdbcTemplate.queryForObject(
            sql,
            Boolean::class.java,
            record.skuId,
            companyId,
            record.description,
            record.ncmCode,
            record.originState,
            record.destinationState,
            payload
        ) ?: false
    }

    fun update(skuId: String, record: InventoryUpdateRequest, companyId: UUID): Boolean {
        val sql = """
            UPDATE inventory_transition
            SET description = ?,
                ncm_code = ?,
                origin_state = ?,
                destination_state = ?,
                legacy_taxes = ?::jsonb,
                is_active = TRUE,
                updated_at = NOW()
            WHERE sku_id = ?
              AND company_id = ?
        """.trimIndent()
        val payload = objectMapper.writeValueAsString(record.legacyTaxes)
        return jdbcTemplate.update(
            sql,
            record.description,
            record.ncmCode,
            record.originState,
            record.destinationState,
            payload,
            skuId,
            companyId
        ) > 0
    }

    fun findBySkuId(skuId: String, companyId: UUID, includeInactive: Boolean): InventoryRecord? {
        val sql = """
            SELECT sku_id, description, ncm_code, origin_state, destination_state, legacy_taxes, reform_taxes, is_active, updated_at
            FROM inventory_transition
            WHERE sku_id = ?
              AND company_id = ?
              AND (is_active = TRUE OR ? = TRUE)
        """.trimIndent()
        return jdbcTemplate.query(sql, inventoryRowMapper(), skuId, companyId, includeInactive).firstOrNull()
    }

    fun list(companyId: UUID, page: Int, limit: Int, includeInactive: Boolean): List<InventoryRecord> {
        require(page > 0) { "page must be > 0" }
        require(limit > 0) { "limit must be > 0" }
        val offset = (page - 1) * limit
        val sql = """
            SELECT sku_id, description, ncm_code, origin_state, destination_state, legacy_taxes, reform_taxes, is_active, updated_at
            FROM inventory_transition
            WHERE company_id = ?
              AND (is_active = TRUE OR ? = TRUE)
            ORDER BY updated_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        return jdbcTemplate.query(sql, inventoryRowMapper(), companyId, includeInactive, limit, offset)
    }

    fun count(companyId: UUID, includeInactive: Boolean): Long {
        val sql = """
            SELECT COUNT(*)
            FROM inventory_transition
            WHERE company_id = ?
              AND (is_active = TRUE OR ? = TRUE)
        """.trimIndent()
        return jdbcTemplate.queryForObject(sql, Long::class.java, companyId, includeInactive) ?: 0L
    }

    fun softDelete(skuId: String, companyId: UUID): Boolean {
        val sql = """
            UPDATE inventory_transition
            SET is_active = FALSE,
                updated_at = NOW()
            WHERE sku_id = ?
              AND company_id = ?
              AND is_active = TRUE
        """.trimIndent()
        return jdbcTemplate.update(sql, skuId, companyId) > 0
    }

    private fun inventoryRowMapper(): RowMapper<InventoryRecord> = RowMapper { rs: ResultSet, _: Int ->
        InventoryRecord(
            skuId = rs.getString("sku_id"),
            description = rs.getString("description"),
            ncmCode = rs.getString("ncm_code"),
            originState = rs.getString("origin_state"),
            destinationState = rs.getString("destination_state"),
            legacyTaxes = readJsonMap(rs.getString("legacy_taxes")),
            reformTaxes = readJsonMap(rs.getString("reform_taxes")),
            isActive = rs.getBoolean("is_active"),
            updatedAt = rs.getTimestamp("updated_at").toInstant()
        )
    }

    private fun readJsonMap(value: String?): Map<String, Double> {
        if (value.isNullOrBlank()) {
            return emptyMap()
        }
        return objectMapper.readValue(value, mapTypeRef)
    }
}
