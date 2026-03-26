package br.fiscalbrain.pipeline

import org.springframework.stereotype.Component

@Component
class ChunkingService {
    private val articleRegex = Regex("(?i)\\bArt\\.??\\s+\\d+[\\w-]*")
    private val headingRegex = Regex("(?im)^(section|chapter|capítulo|título)\\b.*$")

    fun chunk(content: String, maxChunkChars: Int = 1200, overlapChars: Int = 120): List<String> {
        require(maxChunkChars > 0) { "maxChunkChars must be positive" }
        require(overlapChars >= 0) { "overlapChars must be non-negative" }
        require(overlapChars < maxChunkChars) { "overlapChars must be less than maxChunkChars" }

        val normalized = content.replace("\r\n", "\n").trim()
        if (normalized.isBlank()) return emptyList()

        val articleBlocks = splitOnArticles(normalized)
        val chunks = mutableListOf<String>()

        for (block in articleBlocks) {
            chunks += splitWithFallback(block, maxChunkChars, overlapChars)
        }

        return chunks
    }

    private fun splitOnArticles(content: String): List<String> {
        val matches = articleRegex.findAll(content).toList()
        if (matches.isEmpty()) return listOf(content)

        val parts = mutableListOf<String>()
        val firstStart = matches.first().range.first
        if (firstStart > 0) {
            parts.add(content.substring(0, firstStart).trim())
        }
        for ((index, match) in matches.withIndex()) {
            val start = match.range.first
            val end = if (index == matches.lastIndex) content.length else matches[index + 1].range.first
            parts.add(content.substring(start, end).trim())
        }
        return parts
    }

    private fun splitWithFallback(text: String, maxChunkChars: Int, overlapChars: Int): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val endBound = (start + maxChunkChars).coerceAtMost(text.length)
            var chunkEnd = chooseBoundary(text, start, endBound, maxChunkChars)

            val piece = text.substring(start, chunkEnd).trim()
            if (piece.isNotEmpty()) chunks.add(piece)

            if (chunkEnd >= text.length) break
            start = (chunkEnd - overlapChars).coerceAtLeast(start + 1)
        }
        return chunks
    }

    private fun chooseBoundary(text: String, start: Int, endBound: Int, maxChunkChars: Int): Int {
        if (endBound >= text.length) return text.length

        val windowStart = (start + maxChunkChars / 2).coerceAtMost(endBound)
        val nextHeading = headingRegex.find(text, windowStart)?.range?.first
        if (nextHeading != null && nextHeading < endBound) return nextHeading

        val paragraphBreak = text.lastIndexOf("\n\n", startIndex = endBound)
        if (paragraphBreak > start + (maxChunkChars / 2)) return paragraphBreak + 2

        val sentenceBreak = text.lastIndexOf('.', startIndex = endBound)
        if (sentenceBreak > start + (maxChunkChars / 2)) return sentenceBreak + 1

        return endBound
    }
}
