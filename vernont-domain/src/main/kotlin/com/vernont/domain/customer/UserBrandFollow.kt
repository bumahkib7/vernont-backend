package com.vernont.domain.customer

import com.vernont.domain.auth.User
import com.vernont.domain.common.BaseEntity
import com.vernont.domain.product.Brand
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.NamedAttributeNode
import jakarta.persistence.NamedEntityGraph
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "user_brand_follow",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_brand_follow_user_brand", columnNames = ["user_id", "brand_id"])
    ],
    indexes = [
        Index(name = "idx_user_brand_follow_user", columnList = "user_id, deleted_at"),
        Index(name = "idx_user_brand_follow_brand", columnList = "brand_id, deleted_at")
    ]
)
@NamedEntityGraph(
    name = "UserBrandFollow.full",
    attributeNodes = [
        NamedAttributeNode("user"),
        NamedAttributeNode("brand")
    ]
)
class UserBrandFollow : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    lateinit var user: User

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    lateinit var brand: Brand
}
