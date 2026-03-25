package br.fiscalbrain.api

import br.fiscalbrain.core.web.RequestContextKeys
import br.fiscalbrain.splitpayment.SplitPaymentCreateRequest
import br.fiscalbrain.splitpayment.SplitPaymentCreateResponse
import br.fiscalbrain.splitpayment.SplitPaymentListQuery
import br.fiscalbrain.splitpayment.SplitPaymentListResponse
import br.fiscalbrain.splitpayment.SplitPaymentService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/v1/split-payment/events")
class SplitPaymentController(
    private val splitPaymentService: SplitPaymentService
) {
    @PostMapping
    fun create(
        @Valid @RequestBody request: SplitPaymentCreateRequest,
        httpRequest: HttpServletRequest
    ): SplitPaymentCreateResponse =
        splitPaymentService.create(
            request = request,
            requestId = httpRequest.getAttribute(RequestContextKeys.REQUEST_ID_ATTR)?.toString()
        )

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @RequestParam(defaultValue = "50")
        @Min(1)
        @Max(100)
        limit: Int,
        @RequestParam(name = "sku_id", required = false)
        skuId: String?,
        @RequestParam(name = "event_type", required = false)
        eventType: String?
    ): SplitPaymentListResponse =
        splitPaymentService.list(
            SplitPaymentListQuery(
                page = page,
                limit = limit,
                skuId = skuId,
                eventType = eventType
            )
        )
}
