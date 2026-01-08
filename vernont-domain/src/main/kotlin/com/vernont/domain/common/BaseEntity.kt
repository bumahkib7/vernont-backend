package com.vernont.domain.common

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 36, nullable = false, updatable = false, columnDefinition = "VARCHAR(36)")
    open var id: String = generateId()

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    open var createdAt: Instant = Instant.now()

    @UpdateTimestamp
    @Column(nullable = false)
    open var updatedAt: Instant = Instant.now()

    @Column
    open var deletedAt: Instant? = null

    @CreatedBy
    @Column(updatable = false)
    open var createdBy: String? = null

    @LastModifiedBy
    @Column
    open var updatedBy: String? = null

    @Column
    open var deletedBy: String? = null

    // 🔥 JSONB as map
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    open var metadata: MutableMap<String, Any?>? = null

    @Version
    @Column(nullable = false)
    open var version: Long = 0

    fun softDelete(deletedBy: String? = null) {
        this.deletedAt = Instant.now()
        this.deletedBy = deletedBy
    }

    fun restore() {
        this.deletedAt = null
        this.deletedBy = null
    }

    fun isDeleted(): Boolean = deletedAt != null

    companion object {
        fun generateId(): String = java.util.UUID.randomUUID().toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as BaseEntity
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
