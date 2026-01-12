package com.vernont.repository.security

import com.vernont.domain.security.IpListEntry
import com.vernont.domain.security.IpListType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface IpListEntryRepository : JpaRepository<IpListEntry, String> {

    fun findByDeletedAtIsNull(): List<IpListEntry>

    fun findByListTypeAndDeletedAtIsNull(listType: IpListType): List<IpListEntry>

    fun findByIpAddressAndDeletedAtIsNull(ipAddress: String): List<IpListEntry>

    fun findByIpAddressAndListTypeAndDeletedAtIsNull(ipAddress: String, listType: IpListType): IpListEntry?

    fun findByIdAndDeletedAtIsNull(id: String): IpListEntry?

    @Query("SELECT e FROM IpListEntry e WHERE e.deletedAt IS NULL AND (e.expiresAt IS NULL OR e.expiresAt > :now)")
    fun findActiveEntries(now: Instant = Instant.now()): List<IpListEntry>

    @Query("SELECT e FROM IpListEntry e WHERE e.listType = :listType AND e.deletedAt IS NULL AND (e.expiresAt IS NULL OR e.expiresAt > :now)")
    fun findActiveEntriesByType(listType: IpListType, now: Instant = Instant.now()): List<IpListEntry>

    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN TRUE ELSE FALSE END FROM IpListEntry e WHERE e.ipAddress = :ipAddress AND e.listType = :listType AND e.deletedAt IS NULL AND (e.expiresAt IS NULL OR e.expiresAt > :now)")
    fun existsActiveEntry(ipAddress: String, listType: IpListType, now: Instant = Instant.now()): Boolean

    fun existsByIpAddressAndListTypeAndDeletedAtIsNull(ipAddress: String, listType: IpListType): Boolean
}
