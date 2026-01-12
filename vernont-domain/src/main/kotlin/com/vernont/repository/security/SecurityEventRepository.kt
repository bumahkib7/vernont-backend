package com.vernont.repository.security

import com.vernont.domain.security.EventSeverity
import com.vernont.domain.security.SecurityEvent
import com.vernont.domain.security.SecurityEventType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface SecurityEventRepository : JpaRepository<SecurityEvent, String> {

    fun findByDeletedAtIsNull(): List<SecurityEvent>

    fun findByIdAndDeletedAtIsNull(id: String): SecurityEvent?

    fun findByDeletedAtIsNullOrderByCreatedAtDesc(pageable: Pageable): Page<SecurityEvent>

    fun findByEventTypeAndDeletedAtIsNullOrderByCreatedAtDesc(eventType: SecurityEventType): List<SecurityEvent>

    fun findBySeverityAndDeletedAtIsNullOrderByCreatedAtDesc(severity: EventSeverity): List<SecurityEvent>

    fun findByResolvedAndDeletedAtIsNullOrderByCreatedAtDesc(resolved: Boolean): List<SecurityEvent>

    fun findByResolvedAndDeletedAtIsNullOrderByCreatedAtDesc(resolved: Boolean, pageable: Pageable): Page<SecurityEvent>

    fun findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId: String): List<SecurityEvent>

    fun findByIpAddressAndDeletedAtIsNullOrderByCreatedAtDesc(ipAddress: String): List<SecurityEvent>

    @Query("""
        SELECT * FROM security_event e
        WHERE e.deleted_at IS NULL
        AND (CAST(:eventType AS varchar) IS NULL OR e.event_type = CAST(:eventType AS varchar))
        AND (CAST(:severity AS varchar) IS NULL OR e.severity = CAST(:severity AS varchar))
        AND (CAST(:resolved AS boolean) IS NULL OR e.resolved = CAST(:resolved AS boolean))
        ORDER BY e.created_at DESC
    """, nativeQuery = true)
    fun findByFilters(eventType: SecurityEventType?, severity: EventSeverity?, resolved: Boolean?, pageable: Pageable): Page<SecurityEvent>

    @Query("SELECT COUNT(e) FROM SecurityEvent e WHERE e.deletedAt IS NULL AND e.resolved = false")
    fun countUnresolved(): Long

    @Query("SELECT COUNT(e) FROM SecurityEvent e WHERE e.deletedAt IS NULL AND e.createdAt > :since")
    fun countSince(since: Instant): Long

    @Query("SELECT COUNT(e) FROM SecurityEvent e WHERE e.eventType = :eventType AND e.deletedAt IS NULL AND e.createdAt > :since")
    fun countByTypeSince(eventType: SecurityEventType, since: Instant): Long

    @Query("SELECT COUNT(e) FROM SecurityEvent e WHERE e.severity = :severity AND e.deletedAt IS NULL AND e.createdAt > :since")
    fun countBySeveritySince(severity: EventSeverity, since: Instant): Long

    @Query("SELECT COUNT(e) FROM SecurityEvent e WHERE e.eventType IN :types AND e.deletedAt IS NULL AND e.createdAt > :since")
    fun countBlockedAttemptsSince(types: List<SecurityEventType>, since: Instant): Long
}
