package br.fiscalbrain.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress

class OpenAiModelProviderTest {
    private var server: HttpServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    @Test
    fun `generateAudit should fail when audit confidence is missing`() {
        server = HttpServer.create(InetSocketAddress(0), 0).apply {
            createContext("/v1/chat/completions") { exchange ->
                exchange.respondJson(
                    """
                    {
                      "id": "resp-1",
                      "choices": [
                        {
                          "message": {
                            "content": "{\"reform_taxes\":{},\"llm_model_used\":\"gpt-test\",\"source\":{\"law_ref\":\"LC-68\",\"content\":\"texto\",\"source_url\":\"https://example.com\"}}"
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
            }
            start()
        }

        val provider = OpenAiModelProvider(
            objectMapper = ObjectMapper(),
            baseUrl = "http://localhost:${server!!.address.port}/v1",
            apiKey = "test-key",
            embeddingModel = "text-embedding-3-small"
        )

        val ex = assertThrows<IllegalStateException> {
            provider.generateAudit(
                AuditGenerationInput(
                    prompt = "prompt",
                    fallbackLawRef = "LC-68",
                    fallbackSourceUrl = "https://fallback.example",
                    fallbackContent = "fallback",
                    llmModel = "gpt-test"
                )
            )
        }

        assertTrue(ex.message?.contains("audit_confidence") == true)
    }

    private fun HttpExchange.respondJson(body: String) {
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(200, body.toByteArray().size.toLong())
        responseBody.use { it.write(body.toByteArray()) }
    }
}
