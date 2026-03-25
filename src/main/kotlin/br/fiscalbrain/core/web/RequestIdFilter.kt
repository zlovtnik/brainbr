package br.fiscalbrain.core.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class RequestIdFilter : OncePerRequestFilter() {
    private val allowedRequestIdRegex = Regex("^[A-Za-z0-9._:-]{1,64}$")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val requestId = sanitizeRequestId(request.getHeader("X-Request-Id"))
            ?: UUID.randomUUID().toString()

        request.setAttribute(RequestContextKeys.REQUEST_ID_ATTR, requestId)
        response.setHeader("X-Request-Id", requestId)
        MDC.put(RequestContextKeys.REQUEST_ID, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(RequestContextKeys.REQUEST_ID)
        }
    }

    private fun sanitizeRequestId(rawRequestId: String?): String? {
        if (rawRequestId.isNullOrBlank()) {
            return null
        }
        val sanitized = rawRequestId
            .replace(Regex("\\p{Cntrl}"), "")
            .trim()
        if (sanitized.isBlank()) {
            return null
        }
        if (!allowedRequestIdRegex.matches(sanitized)) {
            return null
        }
        return sanitized
    }
}
