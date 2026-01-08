package com.vernont.repository

import com.vernont.domain.audit.AuditAction
import com.vernont.domain.audit.AuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Repository for AuditLog entity
 *
 * Provides queries for:
 * - Entity change history
 * - User activity timeline
 * - Security event tracking
 * - Compliance reporting
 */
@Repository
interface AuditLogRepository : JpaRepository<AuditLog, Long> {

    /**
     * Find all audit logs for a specific entity
     */
    fun findByEntityTypeAndEntityIdOrderByTimestampDesc(
        entityType: String,
        entityId: String,
        pageable: Pageable
    ): Page<AuditLog>

    /**
     * Find all audit logs for a specific user
     */
    fun findByUserIdOrderByTimestampDesc(
        userId: String,
        pageable: Pageable
    ): Page<AuditLog>

    /**
     * Find audit logs by action type
     */
    fun findByActionOrderByTimestampDesc(
        action: AuditAction,
        pageable: Pageable
    ): Page<AuditLog>

    /**
     * Find audit logs within a time range
     */
    fun findByTimestampBetweenOrderByTimestampDesc(
        startTime: Instant,
        endTime: Instant,
        pageable: Pageable
    ): Page<AuditLog>

    /**
     * Find audit logs for a user within a time range
     */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.userId = :userId
        AND a.timestamp BETWEEN :startTime AND :endTime
        ORDER BY a.timestamp DESC
    """)
    fun findByUserIdAndTimeRange(
        @Param("userId") userId: String,
        @Param("startTime") startTime: Instant,
        @Param("endTime") endTime: Instant,
        pageable: Pageable
    ): Page<AuditLog>

    /**
     * Find audit logs for an entity within a time range
     */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.entityType = :entityType
        AND a.entityId = :entityId
        AND a.timestamp BETWEEN :startTime AND :endTime
        ORDER BY a.timestamp DESC
    """)
    fun findByEntityAndTimeRange(
        @Param("entityType") entityType: String,
        @Param("entityId") entityId: String,
        @Param("startTime") startTime: Instant,
        @Param("endTime") endTime: Instant,
        pageable: Pageable
    ): Page<AuditLog>

    /**
     * Find security events (LOGIN, LOGOUT, LOGIN_FAILED, PERMISSION_DENIED)
     */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.action IN ('LOGIN', 'LOGOUT', 'LOGIN_FAILED', 'PERMISSION_DENIED')
        ORDER BY a.timestamp DESC
    """)
    fun findSecurityEvents(pageable: Pageable): Page<AuditLog>

    /**
     * Find failed login attempts for a user
     */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.userId = :userId
        AND a.action = 'LOGIN_FAILED'
        AND a.timestamp >= :since
        ORDER BY a.timestamp DESC
    """)
    fun findFailedLoginAttempts(
        @Param("userId") userId: String,
        @Param("since") since: Instant
    ): List<AuditLog>

    /**
     * Count actions by user within time range
     */
    @Query("""
        SELECT COUNT(a) FROM AuditLog a
        WHERE a.userId = :userId
        AND a.action = :action
        AND a.timestamp BETWEEN :startTime AND :endTime
    """)
    fun countUserActions(
        @Param("userId") userId: String,
        @Param("action") action: AuditAction,
        @Param("startTime") startTime: Instant,
        @Param("endTime") endTime: Instant
    ): Long

    /**
     * Find recent changes to an entity (for change history display)
     */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.entityType = :entityType
        AND a.entityId = :entityId
        AND a.action IN ('CREATE', 'UPDATE', 'DELETE')
        ORDER BY a.timestamp DESC
    """)
    fun findEntityChangeHistory(
        @Param("entityType") entityType: String,
        @Param("entityId") entityId: String,
        pageable: Pageable
    ): Page<AuditLog>

    /**
     * Find all actions by entity type
     */
    fun findByEntityTypeOrderByTimestampDesc(
        entityType: String,
        pageable: Pageable
    ): Page<AuditLog>

    /**
     * Find permission denied events for security monitoring
     */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.action = 'PERMISSION_DENIED'
        AND a.timestamp >= :since
        ORDER BY a.timestamp DESC
    """)
    fun findRecentPermissionDenials(
        @Param("since") since: Instant,
        pageable: Pageable
    ): Page<AuditLog>

    /**
     * Find business events (CREATE, UPDATE, DELETE on business entities)
     * Excludes READ, LOGIN, LOGOUT, LOGIN_FAILED, PERMISSION_DENIED
     */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.action IN ('CREATE', 'UPDATE', 'DELETE')
        ORDER BY a.timestamp DESC
    """)
    fun findBusinessEvents(pageable: Pageable): Page<AuditLog>

    /**
     * Find business events since a specific timestamp (for polling)
     */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.action IN ('CREATE', 'UPDATE', 'DELETE')
        AND a.timestamp > :since
        ORDER BY a.timestamp DESC
    """)
    fun findBusinessEventsSince(
        @Param("since") since: Instant,
        pageable: Pageable
    ): Page<AuditLog>
}
