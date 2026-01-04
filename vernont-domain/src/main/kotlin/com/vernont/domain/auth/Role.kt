package com.vernont.domain.auth

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank

@Entity
@Table(
    name = "role",
    indexes = [
        Index(name = "idx_role_name", columnList = "name", unique = true),
        Index(name = "idx_role_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "Role.full",
    attributeNodes = [
        NamedAttributeNode("permissions")
    ]
)
class Role : BaseEntity() {

    @NotBlank
    @Column(nullable = false, unique = true)
    var name: String = ""

    @Column
    var description: String? = null

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "role_permission",
        joinColumns = [JoinColumn(name = "role_id")],
        inverseJoinColumns = [JoinColumn(name = "permission_id")]
    )
    var permissions: MutableSet<Permission> = mutableSetOf()

    fun addPermission(permission: Permission) {
        permissions.add(permission)
    }

    fun removePermission(permission: Permission) {
        permissions.remove(permission)
    }

    fun hasPermission(permissionName: String): Boolean {
        return permissions.any { it.name == permissionName }
    }

    companion object {
        // Pre-defined roles
        const val GUEST = "GUEST"
        const val CUSTOMER = "CUSTOMER"
        const val ADMIN = "ADMIN"
        const val CUSTOMER_SERVICE = "CUSTOMER_SERVICE"
        const val WAREHOUSE_MANAGER = "WAREHOUSE_MANAGER"
        const val DEVELOPER = "DEVELOPER"
    }
}
