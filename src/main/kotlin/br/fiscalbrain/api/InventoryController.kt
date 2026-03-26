package br.fiscalbrain.api

import br.fiscalbrain.audit.AuditService
import br.fiscalbrain.inventory.InventoryDeleteResponse
import br.fiscalbrain.inventory.InventoryListFilters
import br.fiscalbrain.inventory.InventoryListResponse
import br.fiscalbrain.inventory.InventoryService
import br.fiscalbrain.inventory.InventorySkuResponse
import br.fiscalbrain.inventory.InventoryUpdateRequest
import br.fiscalbrain.inventory.InventoryWriteRequest
import br.fiscalbrain.inventory.InventoryWriteResultResponse
import br.fiscalbrain.audit.ReAuditResponse
import br.fiscalbrain.core.web.RequestContextKeys
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/v1/inventory/sku")
class InventoryController(
    private val inventoryService: InventoryService,
    private val auditService: AuditService
) {
    @PostMapping
    fun upsert(
        @Valid @RequestBody request: InventoryWriteRequest
    ): InventoryWriteResultResponse = inventoryService.upsert(request)

    @GetMapping("/{skuId}")
    fun getBySkuId(
        @PathVariable skuId: String,
        @RequestParam(name = "include_inactive", defaultValue = "false") includeInactive: Boolean
    ): InventorySkuResponse = inventoryService.get(skuId, includeInactive)

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "1")
        @Min(1)
        page: Int,
        @RequestParam(defaultValue = "50")
        @Min(1)
        @Max(100)
        limit: Int,
        @RequestParam(name = "include_inactive", defaultValue = "false")
        includeInactive: Boolean,
        @RequestParam(required = false)
        query: String?,
        @RequestParam(name = "sort_by", required = false)
        sortBy: String?,
        @RequestParam(name = "sort_order", required = false)
        sortOrder: String?
    ): InventoryListResponse = inventoryService.list(
        InventoryListFilters.from(
            page = page,
            limit = limit,
            includeInactive = includeInactive,
            query = query,
            sortBy = sortBy,
            sortOrder = sortOrder
        )
    )

    @PutMapping("/{skuId}")
    fun update(
        @PathVariable skuId: String,
        @Valid @RequestBody request: InventoryUpdateRequest
    ): InventoryWriteResultResponse = inventoryService.update(skuId, request)

    @DeleteMapping("/{skuId}")
    fun delete(
        @PathVariable skuId: String
    ): InventoryDeleteResponse = inventoryService.delete(skuId)

    @PostMapping("/{skuId}/re-audit")
    fun reAudit(
        @PathVariable skuId: String,
        request: HttpServletRequest
    ): ReAuditResponse = auditService.enqueueSkuAudit(
        skuId = skuId,
        requestId = request.getAttribute(RequestContextKeys.REQUEST_ID_ATTR)?.toString()
    )
}
