package br.fiscalbrain.core.security

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
data class JwtSecuritySettings(
    val issuerUri: String? = null,
    val jwkSetUri: String? = null,
    @field:NotBlank
    val tenantClaim: String = "tenant_id"
)
