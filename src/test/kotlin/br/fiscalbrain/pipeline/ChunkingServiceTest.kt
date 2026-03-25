package br.fiscalbrain.pipeline

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChunkingServiceTest {
    private val chunkingService = ChunkingService()

    @Test
    fun `chunk should split long content into multiple non-empty chunks`() {
        val content = buildString {
            repeat(300) {
                append("Regra fiscal importante para teste de chunking. ")
            }
        }

        val chunks = chunkingService.chunk(content, maxChunkChars = 300, overlapChars = 50)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.isNotBlank() })
    }

    @Test
    fun `chunk should return empty list for blank content`() {
        val chunks = chunkingService.chunk("   ")
        assertFalse(chunks.isNotEmpty())
    }
}
