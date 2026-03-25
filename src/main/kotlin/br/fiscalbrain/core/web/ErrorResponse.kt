package br.fiscalbrain.core.web

import com.fasterxml.jackson.annotation.JsonProperty

data class ErrorResponse(
    @JsonProperty("error_code")
    val errorCode: String,
    val message: String,
    @JsonProperty("request_id")
    val requestId: String?
)
