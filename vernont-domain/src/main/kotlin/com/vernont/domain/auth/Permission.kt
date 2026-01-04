package com.vernont.domain.auth

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "permission",
    indexes = [
        Index(name = "idx_permission_name", columnList = "name", unique = true),
        Index(name = "idx_permission_deleted_at", columnList = "deleted_at")
    ]
)
class Permission : BaseEntity() {

    @NotBlank
    @Column(nullable = false, unique = true)
    var name: String = ""

    @Column
    var description: String? = null

    @Column
    var resource: String? = null

    @Column
    var action: String? = null

    companion object {
        // Product permissions
        const val PRODUCT_CREATE = "product:create"
        const val PRODUCT_READ = "product:read"
        const val PRODUCT_UPDATE = "product:update"
        const val PRODUCT_DELETE = "product:delete"

        // Order permissions
        const val ORDER_CREATE = "order:create"
        const val ORDER_READ = "order:read"
        const val ORDER_UPDATE = "order:update"
        const val ORDER_DELETE = "order:delete"
        const val ORDER_COMPLETE = "order:complete"
        const val ORDER_CANCEL = "order:cancel"

        // Customer permissions
        const val CUSTOMER_CREATE = "customer:create"
        const val CUSTOMER_READ = "customer:read"
        const val CUSTOMER_UPDATE = "customer:update"
        const val CUSTOMER_DELETE = "customer:delete"

        // Inventory permissions
        const val INVENTORY_READ = "inventory:read"
        const val INVENTORY_UPDATE = "inventory:update"

        // Analytics permissions
        const val ANALYTICS_READ = "analytics:read"

        // Admin permissions
        const val ADMIN_ALL = "admin:*"
    }
}
