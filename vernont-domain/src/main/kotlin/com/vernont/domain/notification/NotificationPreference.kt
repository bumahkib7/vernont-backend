package com.vernont.domain.notification

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*

/**
 * User notification preferences per event type.
 * Controls whether browser push and in-app notifications are enabled.
 */
@Entity
@Table(
    name = "notification_preference",
    indexes = [
        Index(name = "idx_notification_preference_user_id", columnList = "user_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_notification_preference_user_event",
            columnNames = ["user_id", "event_type"]
        )
    ]
)
class NotificationPreference : BaseEntity() {

    @Column(name = "user_id", nullable = false, length = 36)
    var userId: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    var eventType: NotificationEventType = NotificationEventType.ORDER_CREATED

    @Column(name = "browser_enabled", nullable = false)
    var browserEnabled: Boolean = true

    @Column(name = "in_app_enabled", nullable = false)
    var inAppEnabled: Boolean = true

    fun isEnabled(channel: NotificationChannel): Boolean {
        return when (channel) {
            NotificationChannel.BROWSER -> browserEnabled
            NotificationChannel.IN_APP -> inAppEnabled
        }
    }

    companion object {
        fun createDefault(userId: String, eventType: NotificationEventType): NotificationPreference {
            return NotificationPreference().apply {
                this.userId = userId
                this.eventType = eventType
                this.browserEnabled = eventType.defaultBrowserEnabled
                this.inAppEnabled = eventType.defaultInAppEnabled
            }
        }
    }
}
