package com.vernont.domain.notification

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.Instant

/**
 * Notification entity for storing notification history.
 * Used for the notification bell dropdown and history.
 */
@Entity
@Table(
    name = "notification",
    indexes = [
        Index(name = "idx_notification_user_unread", columnList = "user_id, is_read"),
        Index(name = "idx_notification_user_created", columnList = "user_id, created_at"),
        Index(name = "idx_notification_entity", columnList = "entity_type, entity_id")
    ]
)
class Notification : BaseEntity() {

    @Column(name = "user_id", nullable = false, length = 36)
    var userId: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    var eventType: NotificationEventType = NotificationEventType.ORDER_CREATED

    @NotBlank
    @Column(nullable = false)
    var title: String = ""

    @Column(columnDefinition = "TEXT")
    var message: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 50)
    var entityType: NotificationEntityType? = null

    @Column(name = "entity_id", length = 36)
    var entityId: String? = null

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false

    @Column(name = "read_at")
    var readAt: Instant? = null

    fun markAsRead() {
        if (!isRead) {
            isRead = true
            readAt = Instant.now()
        }
    }

    companion object {
        fun create(
            userId: String,
            eventType: NotificationEventType,
            title: String,
            message: String? = null,
            entityType: NotificationEntityType? = null,
            entityId: String? = null
        ): Notification {
            return Notification().apply {
                this.userId = userId
                this.eventType = eventType
                this.title = title
                this.message = message
                this.entityType = entityType
                this.entityId = entityId
            }
        }
    }
}
