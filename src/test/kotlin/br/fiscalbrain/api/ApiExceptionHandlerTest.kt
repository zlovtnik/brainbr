package br.fiscalbrain.api

import br.fiscalbrain.core.security.ForbiddenOperationException
import br.fiscalbrain.core.web.RequestContextKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest

class ApiExceptionHandlerTest {
    private val handler = ApiExceptionHandler()

    @Test
    fun `should map ForbiddenOperationException to forbidden response`() {
        val request = MockHttpServletRequest("GET", "/api/v1/inventory/sku")
        request.setAttribute(RequestContextKeys.REQUEST_ID_ATTR, "req-1")

        val response = handler.handleForbidden(ForbiddenOperationException("Forbidden operation"), request)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("FORBIDDEN", response.body?.errorCode)
        assertEquals("req-1", response.body?.requestId)
    }

    @Test
    fun `should map IllegalStateException to internal server error via generic handler`() {
        val request = MockHttpServletRequest("GET", "/api/v1/inventory/sku")
        request.setAttribute(RequestContextKeys.REQUEST_ID_ATTR, "req-2")

        val response = handler.handleGeneric(IllegalStateException("Unexpected state"), request)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("INTERNAL_SERVER_ERROR", response.body?.errorCode)
        assertEquals("req-2", response.body?.requestId)
    }
}
