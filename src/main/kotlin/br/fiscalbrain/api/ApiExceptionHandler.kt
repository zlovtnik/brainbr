package br.fiscalbrain.api

import br.fiscalbrain.audit.AuditNotFoundException
import br.fiscalbrain.audit.AuditProcessingException
import br.fiscalbrain.core.security.ForbiddenOperationException
import br.fiscalbrain.core.web.ErrorResponse
import br.fiscalbrain.core.web.RequestContextKeys
import br.fiscalbrain.inventory.InventoryNotFoundException
import br.fiscalbrain.pipeline.IngestionException
import br.fiscalbrain.pipeline.SchemaValidationException
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {
    private val logger = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    @ExceptionHandler(InventoryNotFoundException::class)
    fun handleNotFound(ex: InventoryNotFoundException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        buildErrorResponse(
            status = HttpStatus.NOT_FOUND,
            errorCode = "SKU_NOT_FOUND",
            message = ex.message ?: "SKU not found",
            request = request
        )

    @ExceptionHandler(AuditNotFoundException::class)
    fun handleAuditNotFound(ex: AuditNotFoundException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        buildErrorResponse(
            status = HttpStatus.NOT_FOUND,
            errorCode = "AUDIT_NOT_FOUND",
            message = ex.message ?: "Audit data not found",
            request = request
        )

    @ExceptionHandler(MethodArgumentNotValidException::class, ConstraintViolationException::class)
    fun handleValidation(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        buildErrorResponse(
            status = HttpStatus.BAD_REQUEST,
            errorCode = "BAD_REQUEST",
            message = "Invalid request payload",
            request = request
        )

    @ExceptionHandler(IllegalArgumentException::class, SchemaValidationException::class, IngestionException::class)
    fun handleDomainValidation(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        buildErrorResponse(
            status = HttpStatus.BAD_REQUEST,
            errorCode = "BAD_REQUEST",
            message = ex.message ?: "Invalid request payload",
            request = request
        )

    @ExceptionHandler(AuditProcessingException::class)
    fun handleAuditProcessing(ex: AuditProcessingException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        buildErrorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            errorCode = "AUDIT_PROCESSING_ERROR",
            message = ex.message ?: "Failed to process audit",
            request = request
        )

    @ExceptionHandler(ForbiddenOperationException::class)
    fun handleForbidden(ex: ForbiddenOperationException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        buildErrorResponse(
            status = HttpStatus.FORBIDDEN,
            errorCode = "FORBIDDEN",
            message = ex.message ?: "Forbidden",
            request = request
        )

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error handling request {} {}", request.method, request.requestURI, ex)
        return buildErrorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            errorCode = "INTERNAL_SERVER_ERROR",
            message = "Unexpected error",
            request = request
        )
    }

    private fun buildErrorResponse(
        status: HttpStatus,
        errorCode: String,
        message: String,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val requestId = request.getAttribute(RequestContextKeys.REQUEST_ID_ATTR)?.toString()
        return ResponseEntity.status(status)
            .body(
                ErrorResponse(
                    errorCode = errorCode,
                    message = message,
                    requestId = requestId
                )
            )
    }
}
