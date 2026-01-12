package com.vernont.repository.security

import com.vernont.domain.security.IpIntelligenceCache
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface IpIntelligenceCacheRepository : JpaRepository<IpIntelligenceCache, String> {

    fun findByIpAddressAndDeletedAtIsNull(ipAddress: String): IpIntelligenceCache?

    @Query("SELECT c FROM IpIntelligenceCache c WHERE c.ipAddress = :ipAddress AND c.deletedAt IS NULL AND c.expiresAt > :now")
    fun findValidByIpAddress(ipAddress: String, now: Instant = Instant.now()): IpIntelligenceCache?

    fun findByDeletedAtIsNull(): List<IpIntelligenceCache>

    @Query("SELECT c FROM IpIntelligenceCache c WHERE c.deletedAt IS NULL AND c.expiresAt < :now")
    fun findExpiredEntries(now: Instant = Instant.now()): List<IpIntelligenceCache>

    @Modifying
    @Query("DELETE FROM IpIntelligenceCache c WHERE c.expiresAt < :now")
    fun deleteExpired(now: Instant = Instant.now()): Int

    fun existsByIpAddressAndDeletedAtIsNull(ipAddress: String): Boolean
}
