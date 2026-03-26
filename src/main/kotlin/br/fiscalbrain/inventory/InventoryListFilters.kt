package br.fiscalbrain.inventory

import br.fiscalbrain.core.web.InvalidRequestException

data class InventoryListFilters(
    val page: Int,
    val limit: Int,
    val includeInactive: Boolean,
    val query: String?,
    val sortBy: InventorySortBy,
    val sortOrder: InventorySortOrder
) {
    companion object {
        fun from(
            page: Int,
            limit: Int,
            includeInactive: Boolean,
            query: String?,
            sortBy: String?,
            sortOrder: String?
        ): InventoryListFilters = InventoryListFilters(
            page = page,
            limit = limit,
            includeInactive = includeInactive,
            query = query?.trim()?.takeIf { it.isNotEmpty() },
            sortBy = InventorySortBy.from(sortBy),
            sortOrder = InventorySortOrder.from(sortOrder)
        )
    }
}

enum class InventorySortBy(val apiValue: String) {
    UPDATED_AT("updated_at"),
    SKU_ID("sku_id");

    companion object {
        fun from(value: String?): InventorySortBy {
            val normalized = value?.trim()?.lowercase()
            return entries.firstOrNull { it.apiValue == normalized } ?: if (normalized == null) {
                UPDATED_AT
            } else {
                throw InvalidRequestException(
                    "Invalid sort_by value. Supported values: ${entries.joinToString(", ") { it.apiValue }}"
                )
            }
        }
    }
}

enum class InventorySortOrder(val apiValue: String) {
    ASC("asc"),
    DESC("desc");

    companion object {
        fun from(value: String?): InventorySortOrder {
            val normalized = value?.trim()?.lowercase()
            return entries.firstOrNull { it.apiValue == normalized } ?: if (normalized == null) {
                DESC
            } else {
                throw InvalidRequestException(
                    "Invalid sort_order value. Supported values: ${entries.joinToString(", ") { it.apiValue }}"
                )
            }
        }
    }
}
