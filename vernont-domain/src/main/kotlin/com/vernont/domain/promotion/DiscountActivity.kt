package com.vernont.domain.promotion

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "discount_activity",
    indexes = [
        Index(name = "idx_discount_activity_promotion", columnList = "promotion_id"),
        Index(name = "idx_discount_activity_type", columnList = "activity_type"),
        Index(name = "idx_discount_activity_created", columnList = "created_at")
    ]
)
class DiscountActivity : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    var promotion: Promotion? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false)
    var activityType: DiscountActivityType = DiscountActivityType.PROMOTION_CREATED

    @Column(columnDefinition = "TEXT")
    var description: String? = null

    @Column(name = "actor_id")
    var actorId: String? = null

    @Column(name = "actor_name")
    var actorName: String? = null



    companion object {
        fun create(
            promotion: Promotion?,
            activityType: DiscountActivityType,
            description: String,
            actorId: String? = null,
            actorName: String? = null
        ): DiscountActivity {
            return DiscountActivity().apply {
                this.promotion = promotion
                this.activityType = activityType
                this.description = description
                this.actorId = actorId
                this.actorName = actorName
            }
        }
    }
}

enum class DiscountActivityType {
    PROMOTION_CREATED,
    PROMOTION_UPDATED,
    PROMOTION_ACTIVATED,
    PROMOTION_DEACTIVATED,
    PROMOTION_DELETED,
    CODE_REDEEMED,
    USAGE_LIMIT_REACHED,
    PROMOTION_EXPIRED,
    PROMOTION_DUPLICATED
}
