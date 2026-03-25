package br.fiscalbrain.inventory

import br.fiscalbrain.core.tenant.TenantDbSessionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InventoryService(
    private val inventoryRepository: InventoryRepository,
    private val tenantDbSessionService: TenantDbSessionService
) {
    @Transactional
    fun upsert(request: InventoryWriteRequest): InventoryWriteResultResponse {
        val companyId = tenantDbSessionService.requireCompanyId()
        tenantDbSessionService.apply(companyId)
        val created = inventoryRepository.upsert(request, companyId)
        return InventoryWriteResultResponse(
            skuId = request.skuId,
            status = if (created) "created" else "updated"
        )
    }

    @Transactional(readOnly = true)
    fun get(skuId: String, includeInactive: Boolean = false): InventorySkuResponse {
        val companyId = tenantDbSessionService.requireCompanyId()
        tenantDbSessionService.apply(companyId)
        val record = inventoryRepository.findBySkuId(skuId, companyId, includeInactive)
            ?: throw InventoryNotFoundException("SKU $skuId not found")
        return record.toResponse()
    }

    @Transactional(readOnly = true)
    fun list(page: Int, limit: Int, includeInactive: Boolean): InventoryListResponse {
        val companyId = tenantDbSessionService.requireCompanyId()
        tenantDbSessionService.apply(companyId)
        val items = inventoryRepository.list(companyId, page, limit, includeInactive).map { it.toResponse() }
        val totalCount = inventoryRepository.count(companyId, includeInactive)
        return InventoryListResponse(
            items = items,
            totalCount = totalCount,
            page = page,
            limit = limit,
            hasMore = (page.toLong() * limit) < totalCount
        )
    }

    @Transactional
    fun update(skuId: String, request: InventoryUpdateRequest): InventoryWriteResultResponse {
        val companyId = tenantDbSessionService.requireCompanyId()
        tenantDbSessionService.apply(companyId)
        val updated = inventoryRepository.update(skuId, request, companyId)
        if (!updated) {
            throw InventoryNotFoundException("SKU $skuId not found")
        }
        return InventoryWriteResultResponse(
            skuId = skuId,
            status = "updated"
        )
    }

    @Transactional
    fun delete(skuId: String): InventoryDeleteResponse {
        val companyId = tenantDbSessionService.requireCompanyId()
        tenantDbSessionService.apply(companyId)
        val deleted = inventoryRepository.softDelete(skuId, companyId)
        if (!deleted) {
            throw InventoryNotFoundException("SKU $skuId not found")
        }
        return InventoryDeleteResponse(
            skuId = skuId,
            status = "deleted"
        )
    }

    private fun InventoryRecord.toResponse(): InventorySkuResponse =
        InventorySkuResponse(
            skuId = skuId,
            description = description,
            ncmCode = ncmCode,
            originState = originState,
            destinationState = destinationState,
            legacyTaxes = legacyTaxes,
            reformTaxes = reformTaxes,
            isActive = isActive,
            updatedAt = updatedAt
        )
}
