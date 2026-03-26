package br.fiscalbrain.api

import br.fiscalbrain.inventory.ImpactResponse
import br.fiscalbrain.inventory.InventoryReportingService
import br.fiscalbrain.inventory.PageQuery
import br.fiscalbrain.inventory.RiskQuery
import br.fiscalbrain.inventory.RiskResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/inventory")
class InventoryReportingController(
    private val inventoryReportingService: InventoryReportingService
) {

    @GetMapping("/impact")
    fun impact(@Valid @ModelAttribute query: PageQuery): ImpactResponse =
        inventoryReportingService.impact(query)

    @GetMapping("/risk")
    fun risk(@Valid @ModelAttribute query: RiskQuery): RiskResponse =
        inventoryReportingService.risk(query)
}
