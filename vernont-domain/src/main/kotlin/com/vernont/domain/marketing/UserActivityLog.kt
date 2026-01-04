package com.vernont.domain.marketing

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(
    name = "user_activity_log",
    indexes = [
        Index(name = "idx_user_activity_user_time", columnList = "user_id, created_at"),
        Index(name = "idx_user_activity_type", columnList = "activity_type, created_at")
    ]
)
class UserActivityLog {

    @Id
    @Column(length = 36, nullable = false)
    var id: String = java.util.UUID.randomUUID().toString()

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "user_id", nullable = false, length = 36)
    var userId: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false)
    var activityType: ActivityType = ActivityType.PAGE_VIEW

    @Column(name = "product_id", length = 36)
    var productId: String? = null

    @Column(name = "brand_id", length = 36)
    var brandId: String? = null

    @Column(name = "category_id", length = 36)
    var categoryId: String? = null

    @Column(name = "session_id")
    var sessionId: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var metadata: MutableMap<String, Any?>? = null
}

enum class ActivityType {
    PAGE_VIEW,
    PRODUCT_VIEW,
    SEARCH,
    FAVORITE_ADD,
    CLICK_OUT
}
