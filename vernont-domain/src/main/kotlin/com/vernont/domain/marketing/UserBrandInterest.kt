package com.vernont.domain.marketing

import com.vernont.domain.product.Brand
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "user_brand_interest",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_brand_interest", columnNames = ["user_id", "brand_id"])
    ],
    indexes = [
        Index(name = "idx_brand_interest_user", columnList = "user_id, interest_score"),
        Index(name = "idx_brand_interest_brand", columnList = "brand_id, interest_score")
    ]
)
class UserBrandInterest {

    @Id
    @Column(length = 36, nullable = false)
    var id: String = java.util.UUID.randomUUID().toString()

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @Column(name = "user_id", nullable = false, length = 36)
    var userId: String = ""

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    lateinit var brand: Brand

    @Column(name = "interest_score", nullable = false)
    var interestScore: Int = 1

    @Column(name = "last_interaction_at", nullable = false)
    var lastInteractionAt: Instant = Instant.now()

    fun incrementInterest() {
        interestScore++
        lastInteractionAt = Instant.now()
        updatedAt = Instant.now()
    }
}
