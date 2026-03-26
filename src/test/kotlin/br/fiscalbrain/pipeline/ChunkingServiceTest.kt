package br.fiscalbrain.pipeline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChunkingServiceTest {
    private val service = ChunkingService()

    @Test
    fun `splits on articles first`() {
        val input = """
            Art. 1 This is article one content.
            It continues here.

            Art. 2 This is article two.
        """.trimIndent()

        val chunks = service.chunk(input, maxChunkChars = 80, overlapChars = 10)
        assertEquals(2, chunks.size)
        assertTrue(chunks[0].startsWith("Art. 1"))
        assertTrue(chunks[1].startsWith("Art. 2"))
    }

    @Test
    fun `respects overlap when chunking long text`() {
        val text = "Art. 1 " + "A".repeat(1400)
        val chunks = service.chunk(text, maxChunkChars = 400, overlapChars = 40)
        assertTrue(chunks.size > 1)
        val firstEnd = chunks[0].takeLast(40)
        val secondStart = chunks[1].take(40)
        assertEquals(firstEnd, secondStart)
    }
}
