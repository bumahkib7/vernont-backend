package com.vernont.repository.security

import com.vernont.domain.security.AdminSession
import com.vernont.domain.security.SessionStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface AdminSessionRepository : JpaRepository<AdminSession, String> {

    fun findByDeletedAtIsNull(): List<AdminSession>

    fun findByIdAndDeletedAtIsNull(id: String): AdminSession?

    fun findBySessionTokenHashAndDeletedAtIsNull(sessionTokenHash: String): AdminSession?

    fun findByUserIdAndDeletedAtIsNull(userId: String): List<AdminSession>

    fun findByUserIdAndStatusAndDeletedAtIsNull(userId: String, status: SessionStatus): List<AdminSession>

    fun findByStatusAndDeletedAtIsNull(status: SessionStatus): List<AdminSession>

    fun findByStatusAndDeletedAtIsNullOrderByLastActivityAtDesc(status: SessionStatus): List<AdminSession>

    @Query("SELECT s FROM AdminSession s WHERE s.status = :status AND s.deletedAt IS NULL ORDER BY s.lastActivityAt DESC")
    fun findActiveSessionsPaged(status: SessionStatus, pageable: Pageable): Page<AdminSession>

    @Query("SELECT s FROM AdminSession s WHERE s.status = 'ACTIVE' AND s.expiresAt < :now AND s.deletedAt IS NULL")
    fun findExpiredActiveSessions(now: Instant = Instant.now()): List<AdminSession>

    @Query("SELECT COUNT(s) FROM AdminSession s WHERE s.userId = :userId AND s.status = 'ACTIVE' AND s.deletedAt IS NULL")
    fun countActiveSessionsByUserId(userId: String): Long

    @Query("SELECT s FROM AdminSession s WHERE s.ipAddress = :ipAddress AND s.status = 'ACTIVE' AND s.deletedAt IS NULL")
    fun findActiveSessionsByIpAddress(ipAddress: String): List<AdminSession>

    @Modifying
    @Query("UPDATE AdminSession s SET s.status = 'EXPIRED' WHERE s.status = 'ACTIVE' AND s.expiresAt < :now AND s.deletedAt IS NULL")
    fun expireOldSessions(now: Instant = Instant.now()): Int

    @Query("SELECT COUNT(s) FROM AdminSession s WHERE s.status = 'ACTIVE' AND s.deletedAt IS NULL")
    fun countActiveSessions(): Long

    @Query("SELECT COUNT(s) FROM AdminSession s WHERE s.flaggedVpn = true AND s.createdAt > :since AND s.deletedAt IS NULL")
    fun countVpnFlaggedSessions(since: Instant): Long

    @Query("SELECT COUNT(s) FROM AdminSession s WHERE s.flaggedProxy = true AND s.createdAt > :since AND s.deletedAt IS NULL")
    fun countProxyFlaggedSessions(since: Instant): Long
}
