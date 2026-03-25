package br.fiscalbrain.pipeline

import org.springframework.stereotype.Component

@Component
class ChunkingService {
    fun chunk(content: String, maxChunkChars: Int = 1200, overlapChars: Int = 120): List<String> {
        require(maxChunkChars > 0) { "maxChunkChars must be positive" }
        require(overlapChars >= 0) { "overlapChars must be non-negative" }
        require(overlapChars < maxChunkChars) { "overlapChars must be less than maxChunkChars" }

        val normalized = content.replace("\r\n", "\n").trim()
        if (normalized.isBlank()) {
            return emptyList()
        }

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < normalized.length) {
            val end = (start + maxChunkChars).coerceAtMost(normalized.length)
            var chunkEnd = end
            if (end < normalized.length) {
                val paragraphBreak = normalized.lastIndexOf("\n\n", startIndex = end)
                if (paragraphBreak > start + (maxChunkChars / 2)) {
                    chunkEnd = paragraphBreak + 2
                } else {
                    val sentenceBreak = normalized.lastIndexOf('.', startIndex = end)
                    if (sentenceBreak > start + (maxChunkChars / 2)) {
                        chunkEnd = sentenceBreak + 1
                    }
                }
            }

            val piece = normalized.substring(start, chunkEnd).trim()
            if (piece.isNotEmpty()) {
                chunks.add(piece)
            }

            if (chunkEnd >= normalized.length) {
                break
            }

            start = (chunkEnd - overlapChars).coerceAtLeast(start + 1)
        }

        return chunks
    }
}
