package com.vernont.application.notification

import com.vernont.domain.notification.NotificationChannel
import com.vernont.domain.notification.NotificationEventType
import com.vernont.domain.notification.NotificationPreference
import com.vernont.repository.notification.NotificationPreferenceRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/**
 * Service for managing notification preferences.
 */
@Service
@Transactional
class NotificationPreferenceService(
    private val notificationPreferenceRepository: NotificationPreferenceRepository
) {

    /**
     * Get all preferences for a user.
     * Returns existing preferences merged with defaults for any missing event types.
     */
    @Transactional(readOnly = true)
    fun getUserPreferences(userId: String): List<NotificationPreferenceDto> {
        val existingPrefs = notificationPreferenceRepository.findByUserIdAndDeletedAtIsNull(userId)
            .associateBy { it.eventType }

        return NotificationEventType.entries.map { eventType ->
            val pref = existingPrefs[eventType]
            NotificationPreferenceDto(
                eventType = eventType.name,
                eventTypeDisplayName = eventType.displayName,
                browserEnabled = pref?.browserEnabled ?: eventType.defaultBrowserEnabled,
                inAppEnabled = pref?.inAppEnabled ?: eventType.defaultInAppEnabled
            )
        }
    }

    /**
     * Update a preference for a specific event type.
     */
    fun updatePreference(
        userId: String,
        eventType: NotificationEventType,
        browserEnabled: Boolean,
        inAppEnabled: Boolean
    ): NotificationPreference {
        val existing = notificationPreferenceRepository.findByUserIdAndEventTypeAndDeletedAtIsNull(userId, eventType)

        val pref = if (existing != null) {
            existing.browserEnabled = browserEnabled
            existing.inAppEnabled = inAppEnabled
            existing
        } else {
            NotificationPreference().apply {
                this.userId = userId
                this.eventType = eventType
                this.browserEnabled = browserEnabled
                this.inAppEnabled = inAppEnabled
            }
        }

        val saved = notificationPreferenceRepository.save(pref)
        logger.info { "Updated preference for user $userId, event $eventType: browser=$browserEnabled, inApp=$inAppEnabled" }
        return saved
    }

    /**
     * Update multiple preferences at once.
     */
    fun updatePreferences(userId: String, updates: List<NotificationPreferenceUpdateDto>): List<NotificationPreference> {
        return updates.mapNotNull { update ->
            val eventType = NotificationEventType.fromString(update.eventType)
            if (eventType == null) {
                logger.warn { "Unknown event type: ${update.eventType}" }
                null
            } else {
                updatePreference(userId, eventType, update.browserEnabled, update.inAppEnabled)
            }
        }
    }

    /**
     * Check if a specific channel is enabled for a user and event type.
     */
    @Transactional(readOnly = true)
    fun isEnabled(userId: String, eventType: NotificationEventType, channel: NotificationChannel): Boolean {
        val pref = notificationPreferenceRepository.findByUserIdAndEventTypeAndDeletedAtIsNull(userId, eventType)

        // If no preference exists, use the default from the event type
        return if (pref != null) {
            pref.isEnabled(channel)
        } else {
            when (channel) {
                NotificationChannel.BROWSER -> eventType.defaultBrowserEnabled
                NotificationChannel.IN_APP -> eventType.defaultInAppEnabled
            }
        }
    }

    /**
     * Get all user IDs that have in-app notifications enabled for an event type.
     */
    @Transactional(readOnly = true)
    fun getUsersWithInAppEnabled(eventType: NotificationEventType): List<String> {
        return notificationPreferenceRepository.findUserIdsWithInAppEnabled(eventType)
    }

    /**
     * Get all user IDs that have browser notifications enabled for an event type.
     */
    @Transactional(readOnly = true)
    fun getUsersWithBrowserEnabled(eventType: NotificationEventType): List<String> {
        return notificationPreferenceRepository.findUserIdsWithBrowserEnabled(eventType)
    }

    /**
     * Reset all preferences to defaults for a user.
     */
    fun resetToDefaults(userId: String) {
        val existing = notificationPreferenceRepository.findByUserIdAndDeletedAtIsNull(userId)
        existing.forEach { pref ->
            pref.softDelete(userId)
        }
        notificationPreferenceRepository.saveAll(existing)
        logger.info { "Reset ${existing.size} preferences to defaults for user $userId" }
    }
}

/**
 * DTO for notification preference.
 */
data class NotificationPreferenceDto(
    val eventType: String,
    val eventTypeDisplayName: String,
    val browserEnabled: Boolean,
    val inAppEnabled: Boolean
)

/**
 * DTO for updating a notification preference.
 */
data class NotificationPreferenceUpdateDto(
    val eventType: String,
    val browserEnabled: Boolean,
    val inAppEnabled: Boolean
)
