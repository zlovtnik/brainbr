package br.fiscalbrain.splitpayment

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.util.UUID

class SplitPaymentRepositoryTest {
    private val jdbcTemplate = mock(JdbcTemplate::class.java)
    private val objectMapper = ObjectMapper()
    private val repository = SplitPaymentRepository(jdbcTemplate, objectMapper)

    @Test
    fun `list should use long offset for large page values`() {
        `when`(
            jdbcTemplate.query(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any<RowMapper<SplitPaymentEventRecord>>(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
            )
        ).thenReturn(emptyList())

        repository.list(
            companyId = UUID.randomUUID(),
            page = Int.MAX_VALUE,
            limit = 100,
            skuId = null,
            eventType = null
        )

        val offsetCaptor = ArgumentCaptor.forClass(Any::class.java)
        verify(jdbcTemplate).query(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any<RowMapper<SplitPaymentEventRecord>>(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            offsetCaptor.capture()
        )

        assertTrue(offsetCaptor.value is Long)
        assertTrue((offsetCaptor.value as Long) > Int.MAX_VALUE.toLong())
    }
}
