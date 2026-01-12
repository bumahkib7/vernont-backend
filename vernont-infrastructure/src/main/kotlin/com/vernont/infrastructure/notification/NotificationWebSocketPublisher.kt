package com.vernont.infrastructure.notification

import com.vernont.domain.notification.Notification
import com.vernont.domain.notification.NotificationEntityType
import com.vernont.domain.notification.NotificationEventType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * WebSocket publisher for real-time notification delivery.
 * Publishes to user-specific queues so each user receives only their notifications.
 */
@Service
class NotificationWebSocketPublisher(
    private val messagingTemplate: SimpMessagingTemplate
) {
    companion object {
        const val USER_NOTIFICATION_DESTINATION = "/queue/notifications"
    }

    /**
     * Send a notification to a specific user via their personal queue.
     * @param userIdentifier The user's email (used as STOMP principal name)
     * @param notification The notification to send
     */
    fun sendToUser(userIdentifier: String, notification: Notification) {
        val message = NotificationWebSocketMessage(
            id = notification.id,
            eventType = notification.eventType.name,
            title = notification.title,
            message = notification.message,
            entityType = notification.entityType?.name,
            entityId = notification.entityId,
            navigateTo = buildNavigateTo(notification.entityType, notification.entityId),
            createdAt = notification.createdAt
        )

        logger.info { "Sending WebSocket notification to $userIdentifier: ${notification.title} (destination: /user/$userIdentifier$USER_NOTIFICATION_DESTINATION)" }
        try {
            messagingTemplate.convertAndSendToUser(userIdentifier, USER_NOTIFICATION_DESTINATION, message)
            logger.info { "Successfully sent notification ${notification.id} to $userIdentifier" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to send WebSocket notification ${notification.id} to $userIdentifier" }
        }
    }

    /**
     * Send notification to multiple users.
     * @param userIdentifiers List of user emails (used as STOMP principal names)
     * @param notification The notification to send
     */
    fun sendToUsers(userIdentifiers: List<String>, notification: Notification) {
        userIdentifiers.forEach { userIdentifier ->
            sendToUser(userIdentifier, notification)
        }
    }

    /**
     * Build navigation path based on entity type and ID.
     */
    private fun buildNavigateTo(entityType: NotificationEntityType?, entityId: String?): String? {
        if (entityType == null || entityId == null) return null

        return when (entityType) {
            NotificationEntityType.ORDER -> "/orders/$entityId"
            NotificationEntityType.CUSTOMER -> "/customers/$entityId"
            NotificationEntityType.PRODUCT -> "/products/$entityId"
            NotificationEntityType.SECURITY_EVENT -> "/settings/security"
        }
    }
}

/**
 * WebSocket message payload for notifications.
 */
data class NotificationWebSocketMessage(
    val id: String,
    val eventType: String,
    val title: String,
    val message: String?,
    val entityType: String?,
    val entityId: String?,
    val navigateTo: String?,
    val createdAt: Instant,
    val timestamp: Instant = Instant.now()
)
