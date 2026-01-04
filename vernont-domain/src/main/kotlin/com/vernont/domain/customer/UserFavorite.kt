package com.vernont.domain.customer

import com.vernont.domain.auth.User
import com.vernont.domain.common.BaseEntity
import com.vernont.domain.product.Product
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(
    name = "user_favorite",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_favorite_user_product", columnNames = ["user_id", "product_id"])
    ],
    indexes = [
        Index(name = "idx_user_favorite_user", columnList = "user_id, deleted_at"),
        Index(name = "idx_user_favorite_product", columnList = "product_id, deleted_at")
    ]
)
@NamedEntityGraph(
    name = "UserFavorite.full",
    attributeNodes = [
        NamedAttributeNode("user"),
        NamedAttributeNode("product")
    ]
)
class UserFavorite : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    lateinit var product: Product

    /**
     * User opted-in alert flag. Alerts can be triggered from price or inventory changes.
     */
    @Column(name = "alert_enabled", nullable = false)
    var alertEnabled: Boolean = false

    /**
     * Optional user-specified threshold to trigger price-drop alert.
     */
    @Column(name = "price_threshold")
    var priceThreshold: BigDecimal? = null
}
