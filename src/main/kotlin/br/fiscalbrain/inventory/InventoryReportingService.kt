package br.fiscalbrain.inventory

import br.fiscalbrain.core.tenant.TenantDbSessionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InventoryReportingService(
    private val reportingRepository: InventoryReportingRepository,
    private val tenantDbSessionService: TenantDbSessionService
) {

    @Transactional(readOnly = true)
    fun impact(query: PageQuery): ImpactResponse {
        val companyId = tenantDbSessionService.requireCompanyId()
        tenantDbSessionService.apply(companyId)

        val result = reportingRepository.listImpact(companyId, query)
        val hasMore = (query.page.toLong() * query.limit) < result.totalCount
        return ImpactResponse(
            data = result.items,
            totalCount = result.totalCount,
            page = query.page,
            limit = query.limit,
            hasMore = hasMore
        )
    }

    @Transactional(readOnly = true)
    fun risk(query: RiskQuery): RiskResponse {
        val companyId = tenantDbSessionService.requireCompanyId()
        tenantDbSessionService.apply(companyId)

        val result = reportingRepository.listRisk(companyId, query)
        val hasMore = (query.page.toLong() * query.limit) < result.totalCount
        return RiskResponse(
            items = result.items,
            totalCount = result.totalCount,
            page = query.page,
            limit = query.limit,
            hasMore = hasMore
        )
    }
}
