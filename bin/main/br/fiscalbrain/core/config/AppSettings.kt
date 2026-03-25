package br.fiscalbrain.core.config

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "app")
data class AppSettings(
    @field:NotNull
    @field:Valid val models: Models,
    @field:NotNull
    @field:Valid val worker: Worker
) {
    data class Models(
        @field:NotBlank val embedding: String,
        @field:NotBlank val llm: String
    )

    data class Worker(
        val heartbeatIntervalMs: Long = 60000
    )
}

