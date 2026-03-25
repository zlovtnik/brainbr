package br.fiscalbrain.api

import br.fiscalbrain.core.config.AppSettings
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/platform")
class PlatformController(
    private val appSettings: AppSettings
) {
    @GetMapping("/info")
    fun info(): Map<String, Any> = mapOf(
        "service" to "fiscalbrain-br",
        "embeddingModel" to appSettings.models.embedding,
        "llmModel" to appSettings.models.llm
    )
}

