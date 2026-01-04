package com.vernont.domain.store

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.Instant

@Entity
@Table(
    name = "api_key",
    indexes = [
        Index(name = "idx_api_key_token", columnList = "token", unique = true),
        Index(name = "idx_api_key_store_id", columnList = "store_id"),
        Index(name = "idx_api_key_revoked", columnList = "revoked"),
        Index(name = "idx_api_key_deleted_at", columnList = "deleted_at")
    ]
)
@NamedEntityGraph(
    name = "ApiKey.full",
    attributeNodes = [
        NamedAttributeNode("store")
    ]
)
@NamedEntityGraph(
    name = "ApiKey.withStore",
    attributeNodes = [
        NamedAttributeNode("store")
    ]
)
class ApiKey : BaseEntity() {

    @NotBlank
    @Column(nullable = false, unique = true)
    var token: String = ""

    @NotBlank
    @Column(nullable = false)
    var title: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: ApiKeyType = ApiKeyType.PUBLISHABLE

    @Column(nullable = false)
    var revoked: Boolean = false

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null

    @Column(name = "revoked_by")
    var revokedBy: String? = null

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    var store: Store? = null

    fun revoke(revokedBy: String? = null) {
        this.revoked = true
        this.revokedAt = Instant.now()
        this.revokedBy = revokedBy
    }

    fun markAsUsed() {
        this.lastUsedAt = Instant.now()
    }

    fun isValid(): Boolean {
        return !revoked && !isDeleted()
    }

    fun isPublishable(): Boolean {
        return type == ApiKeyType.PUBLISHABLE
    }

    fun isSecret(): Boolean {
        return type == ApiKeyType.SECRET
    }

    companion object {
        fun generateToken(prefix: String = "pk"): String {
            return "${prefix}_${java.util.UUID.randomUUID().toString().replace("-", "")}"
        }
    }
}

enum class ApiKeyType {
    PUBLISHABLE,
    SECRET
}
