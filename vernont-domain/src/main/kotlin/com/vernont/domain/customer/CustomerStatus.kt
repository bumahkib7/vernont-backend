package com.vernont.domain.customer

/**
 * Customer account status.
 */
enum class CustomerStatus(val displayName: String, val canOrder: Boolean) {
    ACTIVE("Active", true),
    SUSPENDED("Suspended", false),
    BANNED("Banned", false);

    companion object {
        fun fromString(value: String): CustomerStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: ACTIVE
        }
    }
}
