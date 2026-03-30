package br.fiscalbrain.api

import br.fiscalbrain.core.tenant.TenantDbSessionService
import br.fiscalbrain.core.web.RequestContextKeys
import br.fiscalbrain.pipeline.IngestionRequest
import br.fiscalbrain.pipeline.IngestionService
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.URL
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@Validated
@RestController
@RequestMapping("/api/v1/ingestion")
class IngestionController(
    private val ingestionService: IngestionService,
    private val tenantDbSessionService: TenantDbSessionService
) {
    @PostMapping("/jobs")
    fun enqueue(
        @Valid @RequestBody request: IngestionEnqueueRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<IngestionEnqueueResponse> {
        ingestionService.validateInput(request.sourceUrl, request.rawContent)

        val companyId = tenantDbSessionService.requireCompanyId()
        val requestId = httpRequest.getAttribute(RequestContextKeys.REQUEST_ID_ATTR)?.toString()

        val jobId = ingestionService.enqueue(
            IngestionRequest(
                companyId = companyId,
                lawRef = request.lawRef,
                lawType = request.lawType,
                sourceUrl = request.sourceUrl,
                rawContent = request.rawContent,
                publishedAt = request.publishedAt,
                effectiveAt = request.effectiveAt,
                tags = request.tags,
                requestId = requestId
            )
        )

        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(IngestionEnqueueResponse(jobId = jobId, status = "queued"))
    }
}

data class IngestionEnqueueRequest(
    @JsonProperty("law_ref")
    @field:NotBlank
    @field:Size(max = 255)
    val lawRef: String,
    @JsonProperty("law_type")
    @field:NotBlank
    @field:Size(max = 50)
    val lawType: String,
    @JsonProperty("source_url")
    @field:URL(message = "source_url must be a valid URL")
    val sourceUrl: String? = null,
    @JsonProperty("raw_content")
    val rawContent: String? = null,
    @JsonProperty("published_at")
    val publishedAt: LocalDate? = null,
    @JsonProperty("effective_at")
    val effectiveAt: LocalDate? = null,
    @field:Size(max = 20)
    val tags: List<String> = emptyList()
)

data class IngestionEnqueueResponse(
    @JsonProperty("job_id")
    val jobId: String,
    val status: String
)
