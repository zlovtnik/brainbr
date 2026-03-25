package br.fiscalbrain.core.tenant

import java.util.UUID

/**
 * Holds the current tenant in a per-thread context during request processing.
 *
 * This uses [ThreadLocal], so callers must always clear state in a `finally` block to avoid
 * leaking tenant context across reused threads. Prefer [withTenant] when possible.
 */
object TenantContextHolder {
    private val currentCompanyId = ThreadLocal<UUID?>()

    /** Sets the current tenant ID for the active thread. Always pair with [clear]. */
    fun set(companyId: UUID) {
        currentCompanyId.set(companyId)
    }

    /** Gets the current tenant ID for the active thread, or null when none is set. */
    fun get(): UUID? = currentCompanyId.get()

    /** Clears tenant context for the active thread. Call in a `finally` block. */
    fun clear() {
        currentCompanyId.remove()
    }

    /**
     * Runs [block] with tenant context set to [companyId] and guarantees cleanup via [clear].
     */
    inline fun <T> withTenant(companyId: UUID, block: () -> T): T {
        set(companyId)
        return try {
            block()
        } finally {
            clear()
        }
    }
}
