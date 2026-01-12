package com.vernont.repository.notification

import com.vernont.domain.notification.Notification
import com.vernont.domain.notification.NotificationEventType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface NotificationRepository : JpaRepository<Notification, String> {

    fun findByIdAndDeletedAtIsNull(id: String): Notification?

    fun findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<Notification>

    fun findByUserIdAndIsReadAndDeletedAtIsNullOrderByCreatedAtDesc(
        userId: String,
        isRead: Boolean,
        pageable: Pageable
    ): Page<Notification>

    fun findByUserIdAndIsReadAndDeletedAtIsNullOrderByCreatedAtDesc(
        userId: String,
        isRead: Boolean
    ): List<Notification>

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId AND n.isRead = false AND n.deletedAt IS NULL")
    fun countUnreadByUserId(userId: String): Long

    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.deletedAt IS NULL ORDER BY n.createdAt DESC")
    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<Notification>

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt, n.updatedAt = :readAt WHERE n.userId = :userId AND n.isRead = false AND n.deletedAt IS NULL")
    fun markAllAsReadByUserId(userId: String, readAt: Instant): Int

    fun findByEntityTypeAndEntityIdAndDeletedAtIsNull(entityType: String, entityId: String): List<Notification>

    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.eventType = :eventType AND n.deletedAt IS NULL ORDER BY n.createdAt DESC")
    fun findByUserIdAndEventType(userId: String, eventType: NotificationEventType, pageable: Pageable): Page<Notification>

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId AND n.deletedAt IS NULL AND n.createdAt > :since")
    fun countByUserIdSince(userId: String, since: Instant): Long
}
