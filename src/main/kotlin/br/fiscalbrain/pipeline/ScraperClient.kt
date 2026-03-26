package br.fiscalbrain.pipeline

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration
import kotlin.math.min

data class ScrapeResult(
    val body: String,
    val statusCode: Int,
    val eTag: String?,
    val lastModified: String?,
    val attempts: Int
)

@Component
class ScraperClient {
    private val logger = LoggerFactory.getLogger(ScraperClient::class.java)

    private val restClient: RestClient = RestClient.builder()
        .requestFactory(
            NoRedirectRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(10))
                setReadTimeout(Duration.ofSeconds(30))
            }
        )
        .defaultHeader(HttpHeaders.USER_AGENT, "fiscalbrain-scraper/1.0")
        .defaultStatusHandler({ true }) { _, _ -> }
        .build()

    fun fetchWithRetry(uri: URI, hostHeader: String? = null, maxAttempts: Int = 3): ScrapeResult {
        var attempt = 0
        var lastError: Exception? = null
        var backoffMs = 500L

        while (attempt < maxAttempts) {
            attempt += 1
            try {
                val response = restClient.get()
                    .uri(uri)
                    .headers { headers ->
                        if (!hostHeader.isNullOrBlank()) {
                            headers[HttpHeaders.HOST] = hostHeader
                        }
                    }
                    .accept(MediaType.TEXT_PLAIN, MediaType.TEXT_HTML)
                    .retrieve()
                    .toEntity(String::class.java)

                val status = response.statusCode
                val body = response.body?.trim().orEmpty()

                if (status.is2xxSuccessful && body.isNotBlank()) {
                    return ScrapeResult(
                        body = body,
                        statusCode = status.value(),
                        eTag = response.headers[HttpHeaders.ETAG]?.firstOrNull(),
                        lastModified = response.headers[HttpHeaders.LAST_MODIFIED]?.firstOrNull(),
                        attempts = attempt
                    )
                }

                if (status.is2xxSuccessful && body.isBlank()) {
                    throw IngestionException("Empty response body for source_url")
                }

                if (status.is3xxRedirection) {
                    throw IngestionException("Redirect responses are not allowed for source_url")
                }

                if (status == HttpStatus.TOO_MANY_REQUESTS || status.is5xxServerError) {
                    logger.warn("Scrape attempt {} failed with status {} for {}", attempt, status, uri)
                    if (attempt >= maxAttempts) {
                        lastError = IngestionException("Exhausted retries with status ${status.value()} for $uri")
                        break
                    }
                    sleepBackoff(backoffMs)
                    backoffMs = min(backoffMs * 2, 8000)
                    continue
                }

                throw IngestionException("Unexpected HTTP status ${status.value()} for source_url")
            } catch (ex: Exception) {
                if (ex is IngestionException) {
                    throw ex
                }
                lastError = ex as? Exception ?: RuntimeException(ex)
                logger.warn("Scrape attempt {} failed for {}: {}", attempt, uri, ex.message)
                if (attempt >= maxAttempts) {
                    break
                }
                sleepBackoff(backoffMs)
                backoffMs = min(backoffMs * 2, 8000)
            }
        }

        throw IngestionException("Failed to fetch source content after $maxAttempts attempts", lastError)
    }

    private fun sleepBackoff(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private class NoRedirectRequestFactory : SimpleClientHttpRequestFactory() {
        override fun prepareConnection(connection: HttpURLConnection, httpMethod: String) {
            connection.instanceFollowRedirects = false
            super.prepareConnection(connection, httpMethod)
        }
    }
}
