package br.fiscalbrain.inventory

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

class InventoryRepositoryTest {
    private val jdbcTemplate: JdbcTemplate = mock(JdbcTemplate::class.java)
    private val objectMapper = ObjectMapper()
    private val repository = InventoryRepository(jdbcTemplate, objectMapper)

    @Test
    fun `list should reject non-positive page`() {
        assertThrows<IllegalArgumentException> {
            repository.list(UUID.randomUUID(), 0, 50, false)
        }
        verifyNoInteractions(jdbcTemplate)
    }

    @Test
    fun `list should reject non-positive limit`() {
        assertThrows<IllegalArgumentException> {
            repository.list(UUID.randomUUID(), 1, 0, false)
        }
        verifyNoInteractions(jdbcTemplate)
    }
}
