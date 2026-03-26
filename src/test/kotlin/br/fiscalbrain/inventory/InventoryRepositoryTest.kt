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
            repository.list(
                UUID.randomUUID(),
                InventoryListFilters.from(
                    page = 0,
                    limit = 50,
                    includeInactive = false,
                    query = null,
                    sortBy = null,
                    sortOrder = null
                )
            )
        }
        verifyNoInteractions(jdbcTemplate)
    }

    @Test
    fun `list should reject non-positive limit`() {
        assertThrows<IllegalArgumentException> {
            repository.list(
                UUID.randomUUID(),
                InventoryListFilters.from(
                    page = 1,
                    limit = 0,
                    includeInactive = false,
                    query = null,
                    sortBy = null,
                    sortOrder = null
                )
            )
        }
        verifyNoInteractions(jdbcTemplate)
    }
}
