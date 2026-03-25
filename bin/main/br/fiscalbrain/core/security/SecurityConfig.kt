package br.fiscalbrain.core.security

import br.fiscalbrain.core.tenant.TenantContextFilter
import br.fiscalbrain.core.web.ErrorResponseWriter
import br.fiscalbrain.core.web.RequestContextKeys
import br.fiscalbrain.core.web.RequestIdFilter
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.context.SecurityContextHolderFilter

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfig(
    private val jwtSecuritySettings: JwtSecuritySettings,
    private val tenantContextFilter: TenantContextFilter,
    private val requestIdFilter: RequestIdFilter,
    private val objectMapper: ObjectMapper
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers("/actuator/health", "/actuator/info", "/api/v1/platform/**").permitAll()
                auth.requestMatchers(HttpMethod.POST, "/api/v1/inventory/sku/*/re-audit").hasAuthority("SCOPE_audit:trigger")
                auth.requestMatchers(HttpMethod.GET, "/api/v1/inventory/**").hasAuthority("SCOPE_inventory:read")
                auth.requestMatchers(HttpMethod.POST, "/api/v1/inventory/**").hasAuthority("SCOPE_inventory:write")
                auth.requestMatchers(HttpMethod.PUT, "/api/v1/inventory/**").hasAuthority("SCOPE_inventory:write")
                auth.requestMatchers(HttpMethod.DELETE, "/api/v1/inventory/**").hasAuthority("SCOPE_inventory:write")
                auth.requestMatchers(HttpMethod.GET, "/api/v1/audit/explain/*/artifact/latest").hasAuthority("SCOPE_compliance:read")
                auth.requestMatchers(HttpMethod.GET, "/api/v1/audit/explain/artifact/runs/**").hasAuthority("SCOPE_compliance:read")
                auth.requestMatchers(HttpMethod.GET, "/api/v1/audit/explain/**").hasAuthority("SCOPE_audit:read")
                auth.requestMatchers(HttpMethod.POST, "/api/v1/audit/query").hasAuthority("SCOPE_audit:query")
                auth.requestMatchers(HttpMethod.POST, "/api/v1/split-payment/events").hasAuthority("SCOPE_split_payment:write")
                auth.requestMatchers(HttpMethod.GET, "/api/v1/split-payment/events").hasAuthority("SCOPE_split_payment:read")
                auth.anyRequest().authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint())
                it.accessDeniedHandler(accessDeniedHandler())
            }
            .oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }

        http.addFilterBefore(requestIdFilter, SecurityContextHolderFilter::class.java)
        http.addFilterAfter(tenantContextFilter, BearerTokenAuthenticationFilter::class.java)
        return http.build()
    }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        val jwkSetUri = jwtSecuritySettings.jwkSetUri?.trim().orEmpty()
        val issuerUri = jwtSecuritySettings.issuerUri?.trim().orEmpty()
        if (jwkSetUri.isNotEmpty()) {
            return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
        }
        if (issuerUri.isNotEmpty()) {
            return JwtDecoders.fromIssuerLocation(issuerUri)
        }
        throw IllegalStateException(
            "Missing JWT configuration at startup. Configure at least one non-empty value: " +
                "APP_SECURITY_JWT_JWK_SET_URI or APP_SECURITY_JWT_ISSUER_URI."
        )
    }

    @Bean
    fun authenticationEntryPoint(): AuthenticationEntryPoint = AuthenticationEntryPoint { request, response, _ ->
        writeError(
            request = request,
            response = response,
            status = HttpServletResponse.SC_UNAUTHORIZED,
            errorCode = "UNAUTHORIZED",
            message = "Missing or invalid credentials"
        )
    }

    @Bean
    fun accessDeniedHandler(): AccessDeniedHandler = AccessDeniedHandler { request, response, _ ->
        writeError(
            request = request,
            response = response,
            status = HttpServletResponse.SC_FORBIDDEN,
            errorCode = "FORBIDDEN",
            message = "Insufficient permissions"
        )
    }

    private fun writeError(
        request: HttpServletRequest,
        response: HttpServletResponse,
        status: Int,
        errorCode: String,
        message: String
    ) {
        val requestId = request.getAttribute(RequestContextKeys.REQUEST_ID_ATTR)?.toString()
        ErrorResponseWriter(objectMapper).write(
            response = response,
            statusCode = status,
            errorCode = errorCode,
            message = message,
            requestId = requestId
        )
    }
}
