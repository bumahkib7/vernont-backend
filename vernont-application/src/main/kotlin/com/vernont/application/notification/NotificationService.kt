package com.vernont.application.notification

import com.vernont.domain.notification.*
import com.vernont.infrastructure.notification.NotificationWebSocketPublisher
import com.vernont.repository.auth.UserRepository
import com.vernont.repository.notification.NotificationRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Service for managing notifications.
 */
@Service
@Transactional
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val notificationPreferenceService: NotificationPreferenceService,
    private val webSocketPublisher: NotificationWebSocketPublisher,
    private val userRepository: UserRepository
) {

    /**
     * Create and send a notification to a specific user.
     * Respects user preferences for notification channels.
     */
    fun createNotification(
        userId: String,
        eventType: NotificationEventType,
        title: String,
        message: String? = null,
        entityType: NotificationEntityType? = null,
        entityId: String? = null
    ): Notification {
        val notification = Notification.create(
            userId = userId,
            eventType = eventType,
            title = title,
            message = message,
            entityType = entityType,
            entityId = entityId
        )

        val saved = notificationRepository.save(notification)
        logger.info { "Created notification ${saved.id} for user $userId: $title" }

        // Check if in-app notifications are enabled for this event type
        if (notificationPreferenceService.isEnabled(userId, eventType, NotificationChannel.IN_APP)) {
            // Look up user's email for WebSocket delivery (Spring STOMP uses email as principal name)
            val user = userRepository.findByIdWithRoles(userId)
            if (user != null) {
                webSocketPublisher.sendToUser(user.email, saved)
            } else {
                logger.warn { "User $userId not found for WebSocket notification delivery" }
            }
        }

        return saved
    }

    /**
     * Create notifications for multiple users.
     */
    fun createNotificationsForUsers(
        userIds: List<String>,
        eventType: NotificationEventType,
        title: String,
        message: String? = null,
        entityType: NotificationEntityType? = null,
        entityId: String? = null
    ): List<Notification> {
        return userIds.mapNotNull { userId ->
            try {
                createNotification(userId, eventType, title, message, entityType, entityId)
            } catch (e: Exception) {
                logger.error(e) { "Failed to create notification for user $userId" }
                null
            }
        }
    }

    /**
     * Get notifications for a user.
     */
    @Transactional(readOnly = true)
    fun getUserNotifications(userId: String, limit: Int = 50): List<Notification> {
        val pageable = PageRequest.of(0, limit)
        return notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable).content
    }

    /**
     * Get unread notifications for a user.
     */
    @Transactional(readOnly = true)
    fun getUnreadNotifications(userId: String, limit: Int = 50): List<Notification> {
        val pageable = PageRequest.of(0, limit)
        return notificationRepository.findByUserIdAndIsReadAndDeletedAtIsNullOrderByCreatedAtDesc(userId, false, pageable).content
    }

    /**
     * Get paginated notifications for a user.
     */
    @Transactional(readOnly = true)
    fun getUserNotificationsPaginated(userId: String, page: Int, size: Int): Page<Notification> {
        val pageable = PageRequest.of(page, size)
        return notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId, pageable)
    }

    /**
     * Get unread notification count for a user.
     */
    @Transactional(readOnly = true)
    fun getUnreadCount(userId: String): Long {
        return notificationRepository.countUnreadByUserId(userId)
    }

    /**
     * Mark a notification as read.
     */
    fun markAsRead(userId: String, notificationId: String): Notification? {
        val notification = notificationRepository.findByIdAndDeletedAtIsNull(notificationId)
            ?: return null

        // Ensure the notification belongs to the user
        if (notification.userId != userId) {
            logger.warn { "User $userId attempted to mark notification $notificationId belonging to ${notification.userId}" }
            return null
        }

        notification.markAsRead()
        return notificationRepository.save(notification)
    }

    /**
     * Mark all notifications as read for a user.
     */
    fun markAllAsRead(userId: String): Int {
        val count = notificationRepository.markAllAsReadByUserId(userId, Instant.now())
        logger.info { "Marked $count notifications as read for user $userId" }
        return count
    }

    /**
     * Delete a notification (soft delete).
     */
    fun deleteNotification(userId: String, notificationId: String): Boolean {
        val notification = notificationRepository.findByIdAndDeletedAtIsNull(notificationId)
            ?: return false

        if (notification.userId != userId) {
            logger.warn { "User $userId attempted to delete notification $notificationId belonging to ${notification.userId}" }
            return false
        }

        notification.softDelete(userId)
        notificationRepository.save(notification)
        return true
    }

    /**
     * Check if browser notifications are enabled for a user and event type.
     */
    @Transactional(readOnly = true)
    fun isBrowserEnabled(userId: String, eventType: NotificationEventType): Boolean {
        return notificationPreferenceService.isEnabled(userId, eventType, NotificationChannel.BROWSER)
    }
}
