package br.fiscalbrain.transition

import br.fiscalbrain.core.tenant.TenantDbSessionService
import br.fiscalbrain.core.web.InvalidRequestException
import br.fiscalbrain.inventory.InventoryNotFoundException
import br.fiscalbrain.inventory.InventoryRepository
import br.fiscalbrain.transition.TransitionMath.blendedBurden
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TransitionService(
    private val transitionCalendarRepository: TransitionCalendarRepository,
    private val inventoryRepository: InventoryRepository,
    private val tenantDbSessionService: TenantDbSessionService
) {

    @Transactional(readOnly = true)
    fun calendar(): TransitionCalendarResponse {
        val years = transitionCalendarRepository.list().map {
            TransitionYearResponse(
                year = it.year,
                reformWeight = it.reformWeight,
                legacyWeight = it.legacyWeight
            )
        }
        return TransitionCalendarResponse(years)
    }

    @Transactional(readOnly = true)
    fun effectiveRate(skuId: String, year: Int): EffectiveRateResponse {
        val weights = transitionCalendarRepository.find(year)
            ?: throw InvalidRequestException("No transition weights configured for year $year")

        val companyId = tenantDbSessionService.requireCompanyId()
        tenantDbSessionService.apply(companyId)

        val record = inventoryRepository.findBySkuId(skuId, companyId, includeInactive = false)
            ?: throw InventoryNotFoundException("SKU $skuId not found")

        val burden = blendedBurden(record.legacyTaxes, record.reformTaxes, weights)

        return EffectiveRateResponse(
            skuId = skuId,
            year = year,
            blendedBurden = BlendedBurdenResponse(
                legacyComponent = burden.legacyComponent,
                reformComponent = burden.reformComponent,
                total = burden.total
            )
        )
    }

    fun computeRiskScore(legacyTaxes: Map<String, Double>, reformTaxes: Map<String, Double>, auditConfidence: Double?): Int {
        val legacyTotal = legacyTaxes.values.sum()
        val reformTotal = reformTaxes.values.sum()
        return TransitionMath.computeRiskScore(legacyTotal, reformTotal, auditConfidence)
    }
}
