package com.vernont.api.controller

import com.vernont.application.notification.*
import com.vernont.domain.auth.UserContext
import com.vernont.domain.notification.Notification
import com.vernont.domain.notification.NotificationEventType
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant

private val logger = KotlinLogging.logger {}

// ============================================================================
// Response DTOs
// ============================================================================

data class NotificationDto(
    val id: String,
    val eventType: String,
    val title: String,
    val message: String?,
    val entityType: String?,
    val entityId: String?,
    val navigateTo: String?,
    val isRead: Boolean,
    val readAt: Instant?,
    val createdAt: Instant
) {
    companion object {
        fun from(notification: Notification): NotificationDto {
            return NotificationDto(
                id = notification.id,
                eventType = notification.eventType.name,
                title = notification.title,
                message = notification.message,
                entityType = notification.entityType?.name,
                entityId = notification.entityId,
                navigateTo = buildNavigateTo(notification),
                isRead = notification.isRead,
                readAt = notification.readAt,
                createdAt = notification.createdAt
            )
        }

        private fun buildNavigateTo(notification: Notification): String? {
            val entityType = notification.entityType ?: return null
            val entityId = notification.entityId ?: return null

            return when (entityType.name) {
                "ORDER" -> "/orders/$entityId"
                "CUSTOMER" -> "/customers/$entityId"
                "PRODUCT" -> "/products/$entityId"
                "SECURITY_EVENT" -> "/settings/security"
                else -> null
            }
        }
    }
}

data class NotificationsResponse(
    val notifications: List<NotificationDto>,
    val count: Int
)

data class UnreadCountResponse(
    val count: Long
)

data class PreferencesResponse(
    val preferences: List<NotificationPreferenceDto>
)

// ============================================================================
// Request DTOs
// ============================================================================

data class UpdatePreferencesRequest(
    val preferences: List<PreferenceUpdate>
)

data class PreferenceUpdate(
    val eventType: String,
    val browserEnabled: Boolean,
    val inAppEnabled: Boolean
)

// ============================================================================
// Controller
// ============================================================================

@RestController
@RequestMapping("/api/v1/internal/notifications")
@Tag(name = "Notifications", description = "User notification management endpoints")
class NotificationController(
    private val notificationService: NotificationService,
    private val notificationPreferenceService: NotificationPreferenceService
) {

    // ========================================================================
    // Notifications
    // ========================================================================

    @Operation(summary = "Get notifications for the current user")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    fun getNotifications(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
        @AuthenticationPrincipal userContext: UserContext
    ): ResponseEntity<NotificationsResponse> {
        logger.debug { "GET /api/v1/internal/notifications - user=${userContext.userId}, unreadOnly=$unreadOnly" }

        val notifications = if (unreadOnly) {
            notificationService.getUnreadNotifications(userContext.userId, limit.coerceAtMost(100))
        } else {
            notificationService.getUserNotifications(userContext.userId, limit.coerceAtMost(100))
        }

        return ResponseEntity.ok(NotificationsResponse(
            notifications = notifications.map { NotificationDto.from(it) },
            count = notifications.size
        ))
    }

    @Operation(summary = "Get unread notification count")
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    fun getUnreadCount(
        @AuthenticationPrincipal userContext: UserContext
    ): ResponseEntity<UnreadCountResponse> {
        logger.debug { "GET /api/v1/internal/notifications/unread-count - user=${userContext.userId}" }

        val count = notificationService.getUnreadCount(userContext.userId)
        return ResponseEntity.ok(UnreadCountResponse(count = count))
    }

    @Operation(summary = "Mark a notification as read")
    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    fun markAsRead(
        @PathVariable id: String,
        @AuthenticationPrincipal userContext: UserContext
    ): ResponseEntity<Any> {
        logger.debug { "POST /api/v1/internal/notifications/$id/read - user=${userContext.userId}" }

        val notification = notificationService.markAsRead(userContext.userId, id)
        return if (notification != null) {
            ResponseEntity.ok(mapOf(
                "id" to id,
                "isRead" to true
            ))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @Operation(summary = "Mark all notifications as read")
    @PostMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    fun markAllAsRead(
        @AuthenticationPrincipal userContext: UserContext
    ): ResponseEntity<Any> {
        logger.debug { "POST /api/v1/internal/notifications/read-all - user=${userContext.userId}" }

        val count = notificationService.markAllAsRead(userContext.userId)
        return ResponseEntity.ok(mapOf(
            "markedAsRead" to count
        ))
    }

    @Operation(summary = "Delete a notification")
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    fun deleteNotification(
        @PathVariable id: String,
        @AuthenticationPrincipal userContext: UserContext
    ): ResponseEntity<Any> {
        logger.debug { "DELETE /api/v1/internal/notifications/$id - user=${userContext.userId}" }

        val deleted = notificationService.deleteNotification(userContext.userId, id)
        return if (deleted) {
            ResponseEntity.ok(mapOf(
                "id" to id,
                "deleted" to true
            ))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    // ========================================================================
    // Preferences
    // ========================================================================

    @Operation(summary = "Get notification preferences")
    @GetMapping("/preferences")
    @PreAuthorize("isAuthenticated()")
    fun getPreferences(
        @AuthenticationPrincipal userContext: UserContext
    ): ResponseEntity<PreferencesResponse> {
        logger.debug { "GET /api/v1/internal/notifications/preferences - user=${userContext.userId}" }

        val preferences = notificationPreferenceService.getUserPreferences(userContext.userId)
        return ResponseEntity.ok(PreferencesResponse(
            preferences = preferences
        ))
    }

    @Operation(summary = "Update notification preferences")
    @PutMapping("/preferences")
    @PreAuthorize("isAuthenticated()")
    fun updatePreferences(
        @RequestBody request: UpdatePreferencesRequest,
        @AuthenticationPrincipal userContext: UserContext
    ): ResponseEntity<PreferencesResponse> {
        logger.debug { "PUT /api/v1/internal/notifications/preferences - user=${userContext.userId}" }

        val updates = request.preferences.map { update ->
            NotificationPreferenceUpdateDto(
                eventType = update.eventType,
                browserEnabled = update.browserEnabled,
                inAppEnabled = update.inAppEnabled
            )
        }

        notificationPreferenceService.updatePreferences(userContext.userId, updates)

        // Return updated preferences
        val preferences = notificationPreferenceService.getUserPreferences(userContext.userId)
        return ResponseEntity.ok(PreferencesResponse(
            preferences = preferences
        ))
    }

    @Operation(summary = "Reset preferences to defaults")
    @PostMapping("/preferences/reset")
    @PreAuthorize("isAuthenticated()")
    fun resetPreferences(
        @AuthenticationPrincipal userContext: UserContext
    ): ResponseEntity<PreferencesResponse> {
        logger.debug { "POST /api/v1/internal/notifications/preferences/reset - user=${userContext.userId}" }

        notificationPreferenceService.resetToDefaults(userContext.userId)

        val preferences = notificationPreferenceService.getUserPreferences(userContext.userId)
        return ResponseEntity.ok(PreferencesResponse(
            preferences = preferences
        ))
    }
}
