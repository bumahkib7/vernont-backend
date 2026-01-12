package com.vernont.repository.notification

import com.vernont.domain.notification.NotificationEventType
import com.vernont.domain.notification.NotificationPreference
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface NotificationPreferenceRepository : JpaRepository<NotificationPreference, String> {

    fun findByIdAndDeletedAtIsNull(id: String): NotificationPreference?

    fun findByUserIdAndDeletedAtIsNull(userId: String): List<NotificationPreference>

    fun findByUserIdAndEventTypeAndDeletedAtIsNull(userId: String, eventType: NotificationEventType): NotificationPreference?

    @Query("SELECT p FROM NotificationPreference p WHERE p.userId = :userId AND p.eventType = :eventType AND p.deletedAt IS NULL")
    fun findPreference(userId: String, eventType: NotificationEventType): NotificationPreference?

    @Query("SELECT p FROM NotificationPreference p WHERE p.userId = :userId AND p.browserEnabled = true AND p.deletedAt IS NULL")
    fun findBrowserEnabledPreferences(userId: String): List<NotificationPreference>

    @Query("SELECT p FROM NotificationPreference p WHERE p.userId = :userId AND p.inAppEnabled = true AND p.deletedAt IS NULL")
    fun findInAppEnabledPreferences(userId: String): List<NotificationPreference>

    @Query("SELECT DISTINCT p.userId FROM NotificationPreference p WHERE p.eventType = :eventType AND p.inAppEnabled = true AND p.deletedAt IS NULL")
    fun findUserIdsWithInAppEnabled(eventType: NotificationEventType): List<String>

    @Query("SELECT DISTINCT p.userId FROM NotificationPreference p WHERE p.eventType = :eventType AND p.browserEnabled = true AND p.deletedAt IS NULL")
    fun findUserIdsWithBrowserEnabled(eventType: NotificationEventType): List<String>
}
