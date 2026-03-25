package br.fiscalbrain.pipeline

import java.security.MessageDigest
import java.text.Normalizer

object VectorUtils {
    fun toVectorLiteral(values: List<Double>): String {
        return values.joinToString(prefix = "[", postfix = "]") { "%.10f".format(java.util.Locale.ROOT, it) }
    }

    fun hashContent(input: String): String {
        val normalized = Normalizer.normalize(input.trim(), Normalizer.Form.NFKC)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
