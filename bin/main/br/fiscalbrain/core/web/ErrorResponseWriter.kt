package br.fiscalbrain.core.web

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType

class ErrorResponseWriter(
    private val objectMapper: ObjectMapper
) {
    fun write(
        response: HttpServletResponse,
        statusCode: Int,
        errorCode: String,
        message: String,
        requestId: String?
    ) {
        response.status = statusCode
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(
            objectMapper.writeValueAsString(
                ErrorResponse(
                    errorCode = errorCode,
                    message = message,
                    requestId = requestId
                )
            )
        )
    }
}
