package com.vernont.domain.product

import jakarta.persistence.*
import java.io.Serializable // Still needed for some entities, so keeping for now.
import java.time.Instant
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "product_collection")
class ProductCollection { // Reverted to original form

    @Id
    @GeneratedValue(generator = "uuid2")
    @Column(name = "id", columnDefinition = "VARCHAR(255)")
    var id: String? = null

    @Column(nullable = false, unique = true)
    var title: String = ""

    @Column(nullable = false, unique = true)
    var handle: String = ""

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Column(columnDefinition = "TEXT")
    var imageUrl: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var metadata: MutableMap<String, Any?>? = null

    @OneToMany(mappedBy = "collection", cascade = [CascadeType.ALL], orphanRemoval = true)
    var products: MutableList<Product> = mutableListOf()

    @Column(nullable = false) // Added
    var published: Boolean = false // Added

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null

    @PrePersist
    protected fun onCreate() {
        createdAt = Instant.now()
        updatedAt = Instant.now()
    }

    @PreUpdate
    protected fun onUpdate() {
        updatedAt = Instant.now()
    }
}
