package br.fiscalbrain.splitpayment

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class SplitPaymentRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    private val anyMapType = object : TypeReference<Map<String, Any>>() {}

    fun createOrGet(
        companyId: UUID,
        request: SplitPaymentCreateRequest,
        normalizedCurrency: String,
        requestId: String?
    ): SplitPaymentCreateResult {
        val insertSql = """
            INSERT INTO split_payment_events (
                company_id, sku_id, event_type, amount, currency, idempotency_key,
                event_timestamp, integration_status, integration_metadata, event_payload, request_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
            ON CONFLICT (company_id, idempotency_key) DO NOTHING
            RETURNING id, sku_id, event_type, amount, currency, idempotency_key,
                      event_timestamp, integration_status,
                      integration_metadata::text AS integration_metadata_json,
                      event_payload::text AS event_payload_json, created_at
        """.trimIndent()

        val inserted = jdbcTemplate.query(
            insertSql,
            splitPaymentRowMapper(),
            companyId,
            request.skuId,
            request.eventType,
            request.amount,
            normalizedCurrency,
            request.idempotencyKey,
            request.timestamp,
            "queued",
            objectMapper.writeValueAsString(request.integrationMetadata),
            objectMapper.writeValueAsString(request.eventPayload),
            requestId
        ).firstOrNull()

        if (inserted != null) {
            appendStatus(
                companyId = companyId,
                eventId = inserted.id,
                status = inserted.integrationStatus,
                statusMetadata = inserted.integrationMetadata,
                requestId = requestId
            )
            return SplitPaymentCreateResult(record = inserted, created = true)
        }

        val existing = findByIdempotencyKey(companyId, request.idempotencyKey)
            ?: throw IllegalStateException("Failed to fetch split payment event after idempotent insert")
        return SplitPaymentCreateResult(record = existing, created = false)
    }

    fun list(
        companyId: UUID,
        page: Int,
        limit: Int,
        skuId: String?,
        eventType: String?
    ): List<SplitPaymentEventRecord> {
        require(page >= 1) { "Invalid pagination: page must be >= 1, got page=$page" }
        require(limit > 0) { "Invalid pagination: limit must be > 0, got limit=$limit" }

        val offset = (page - 1L) * limit.toLong()
        val args = mutableListOf<Any>(companyId)

        val sql = StringBuilder(
            """
            SELECT e.id,
                   e.sku_id,
                   e.event_type,
                   e.amount,
                   e.currency,
                   e.idempotency_key,
                   e.event_timestamp,
                   COALESCE(st.status, e.integration_status) AS integration_status,
                   COALESCE(st.status_metadata_json, e.integration_metadata::text) AS integration_metadata_json,
                   e.event_payload::text AS event_payload_json,
                   e.created_at
            FROM split_payment_events e
            LEFT JOIN LATERAL (
                SELECT s.status,
                       s.status_metadata::text AS status_metadata_json
                FROM split_payment_event_statuses s
                WHERE s.company_id = e.company_id
                  AND s.event_id = e.id
                ORDER BY s.changed_at DESC, s.id DESC
                LIMIT 1
            ) st ON TRUE
            WHERE e.company_id = ?
            """.trimIndent()
        )

        if (!skuId.isNullOrBlank()) {
            sql.append(" AND e.sku_id = ?")
            args.add(skuId)
        }

        if (!eventType.isNullOrBlank()) {
            sql.append(" AND e.event_type = ?")
            args.add(eventType)
        }

        sql.append(" ORDER BY e.created_at DESC LIMIT ? OFFSET ?")
        args.add(limit)
        args.add(offset)

        return jdbcTemplate.query(sql.toString(), splitPaymentRowMapper(), *args.toTypedArray())
    }

    fun count(companyId: UUID, skuId: String?, eventType: String?): Long {
        val args = mutableListOf<Any>(companyId)
        val sql = StringBuilder(
            """
            SELECT COUNT(*)
            FROM split_payment_events
            WHERE company_id = ?
            """.trimIndent()
        )

        if (!skuId.isNullOrBlank()) {
            sql.append(" AND sku_id = ?")
            args.add(skuId)
        }

        if (!eventType.isNullOrBlank()) {
            sql.append(" AND event_type = ?")
            args.add(eventType)
        }

        return jdbcTemplate.queryForObject(sql.toString(), Long::class.java, *args.toTypedArray()) ?: 0L
    }

    private fun appendStatus(
        companyId: UUID,
        eventId: UUID,
        status: String,
        statusMetadata: Map<String, Any>,
        requestId: String?
    ) {
        val sql = """
            INSERT INTO split_payment_event_statuses (
                event_id, company_id, status, status_metadata, request_id
            ) VALUES (?, ?, ?, ?::jsonb, ?)
        """.trimIndent()

        jdbcTemplate.update(
            sql,
            eventId,
            companyId,
            status,
            objectMapper.writeValueAsString(statusMetadata),
            requestId
        )
    }

    private fun findByIdempotencyKey(companyId: UUID, idempotencyKey: String): SplitPaymentEventRecord? {
        val sql = """
            SELECT e.id,
                   e.sku_id,
                   e.event_type,
                   e.amount,
                   e.currency,
                   e.idempotency_key,
                   e.event_timestamp,
                   COALESCE(st.status, e.integration_status) AS integration_status,
                   COALESCE(st.status_metadata_json, e.integration_metadata::text) AS integration_metadata_json,
                   e.event_payload::text AS event_payload_json,
                   e.created_at
            FROM split_payment_events e
            LEFT JOIN LATERAL (
                SELECT s.status,
                       s.status_metadata::text AS status_metadata_json
                FROM split_payment_event_statuses s
                WHERE s.company_id = e.company_id
                  AND s.event_id = e.id
                ORDER BY s.changed_at DESC, s.id DESC
                LIMIT 1
            ) st ON TRUE
            WHERE e.company_id = ?
              AND e.idempotency_key = ?
            LIMIT 1
        """.trimIndent()

        return jdbcTemplate.query(sql, splitPaymentRowMapper(), companyId, idempotencyKey).firstOrNull()
    }

    private fun splitPaymentRowMapper(): RowMapper<SplitPaymentEventRecord> = RowMapper { rs: ResultSet, _: Int ->
        SplitPaymentEventRecord(
            id = rs.getObject("id", UUID::class.java),
            skuId = rs.getString("sku_id"),
            eventType = rs.getString("event_type"),
            amount = rs.getLong("amount"),
            currency = rs.getString("currency"),
            idempotencyKey = rs.getString("idempotency_key"),
            timestamp = rs.getTimestamp("event_timestamp").toInstant(),
            integrationStatus = rs.getString("integration_status"),
            integrationMetadata = readAnyMap(rs.getString("integration_metadata_json")),
            eventPayload = readAnyMap(rs.getString("event_payload_json")),
            createdAt = rs.getTimestamp("created_at").toInstant()
        )
    }

    private fun readAnyMap(raw: String?): Map<String, Any> {
        if (raw.isNullOrBlank()) {
            return emptyMap()
        }
        return objectMapper.readValue(raw, anyMapType)
    }
}

data class SplitPaymentEventRecord(
    val id: UUID,
    val skuId: String,
    val eventType: String,
    val amount: Long,
    val currency: String,
    val idempotencyKey: String,
    val timestamp: Instant,
    val integrationStatus: String,
    val integrationMetadata: Map<String, Any>,
    val eventPayload: Map<String, Any>,
    val createdAt: Instant
)

data class SplitPaymentCreateResult(
    val record: SplitPaymentEventRecord,
    val created: Boolean
)
