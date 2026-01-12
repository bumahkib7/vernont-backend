package com.vernont.domain.security

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.Instant

/**
 * Manual IP allowlist/blocklist entries for overriding automated decisions.
 */
@Entity
@Table(
    name = "ip_list_entry",
    indexes = [
        Index(name = "idx_ip_list_entry_ip_address", columnList = "ip_address"),
        Index(name = "idx_ip_list_entry_list_type", columnList = "list_type")
    ]
)
class IpListEntry : BaseEntity() {

    @NotBlank
    @Column(name = "ip_address", nullable = false, length = 45)
    var ipAddress: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "list_type", nullable = false, length = 20)
    var listType: IpListType = IpListType.BLOCKLIST

    @Column(columnDefinition = "TEXT")
    var reason: String? = null

    @Column(name = "expires_at")
    var expiresAt: Instant? = null

    @Column(name = "added_by_user_id", length = 36)
    var addedByUserId: String? = null

    fun isExpired(): Boolean = expiresAt?.isBefore(Instant.now()) == true

    fun isActive(): Boolean = !isExpired() && !isDeleted()
}

enum class IpListType {
    ALLOWLIST,
    BLOCKLIST
}
