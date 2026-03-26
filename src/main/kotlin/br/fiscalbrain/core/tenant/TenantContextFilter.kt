package br.fiscalbrain.core.tenant

import br.fiscalbrain.core.security.JwtSecuritySettings
import br.fiscalbrain.core.web.ErrorResponseWriter
import br.fiscalbrain.core.web.RequestContextKeys
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

class TenantContextFilter(
    private val jwtSecuritySettings: JwtSecuritySettings,
    objectMapper: ObjectMapper,
    private val tenantResolver: TenantResolver
) : OncePerRequestFilter() {
    private val errorResponseWriter = ErrorResponseWriter(objectMapper)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val tenantRequired = requiresTenant(request)
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication is JwtAuthenticationToken && authentication.isAuthenticated) {
                val tenantClaimValue = authentication.token.claims[jwtSecuritySettings.tenantClaim]?.toString()
                val companyId = tenantResolver.resolve(tenantClaimValue)
                if (companyId != null) {
                    TenantContextHolder.set(companyId)
                    MDC.put("company_id", companyId.toString())
                }
            }

            // Downstream services must only use server-validated tenant context from TenantContextHolder.
            // Never fallback to tenant IDs from client payload/query/header for tenant-scoped resources.
            if (tenantRequired && TenantContextHolder.get() == null) {
                if (authentication is JwtAuthenticationToken && authentication.isAuthenticated) {
                    writeTenantClaimError(response, request)
                } else {
                    writeMissingCredentialsError(response, request)
                }
                return
            }

            filterChain.doFilter(request, response)
        } finally {
            TenantContextHolder.clear()
            MDC.remove("company_id")
        }
    }

    private fun requiresTenant(request: HttpServletRequest): Boolean =
        request.requestURI.startsWith("/api/v1/inventory") ||
            request.requestURI.startsWith("/api/v1/audit") ||
            request.requestURI.startsWith("/api/v1/split-payment") ||
            request.requestURI.startsWith("/api/v1/ingestion")

    private fun writeTenantClaimError(response: HttpServletResponse, request: HttpServletRequest) {
        val requestId = request.getAttribute(RequestContextKeys.REQUEST_ID_ATTR)?.toString()
        errorResponseWriter.write(
            response = response,
            statusCode = HttpServletResponse.SC_FORBIDDEN,
            errorCode = "FORBIDDEN",
            message = "Missing or invalid tenant claim",
            requestId = requestId
        )
    }

    private fun writeMissingCredentialsError(response: HttpServletResponse, request: HttpServletRequest) {
        val requestId = request.getAttribute(RequestContextKeys.REQUEST_ID_ATTR)?.toString()
        errorResponseWriter.write(
            response = response,
            statusCode = HttpServletResponse.SC_UNAUTHORIZED,
            errorCode = "UNAUTHORIZED",
            message = "Missing or invalid credentials",
            requestId = requestId
        )
    }
}
