package com.vernont.api.controller.admin

import com.vernont.application.security.SecurityStats
import com.vernont.application.security.SessionTrackingService
import com.vernont.domain.security.*
import com.vernont.infrastructure.security.IpIntelligenceService
import com.vernont.infrastructure.security.SessionDto
import com.vernont.infrastructure.security.SecurityEventDto
import com.vernont.repository.security.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import com.vernont.domain.auth.UserContext
import java.time.Instant

private val logger = KotlinLogging.logger {}

// ============================================================================
// Response DTOs
// ============================================================================

data class ActiveSessionsResponse(
    val sessions: List<SessionDto>,
    val count: Int
)

data class IpListResponse(
    val entries: List<IpListEntryDto>,
    val count: Int
)

data class IpListEntryDto(
    val id: String,
    val ip_address: String,
    val list_type: String,
    val reason: String?,
    val expires_at: Instant?,
    val added_by_user_id: String?,
    val created_at: Instant
) {
    companion object {
        fun from(entry: IpListEntry): IpListEntryDto {
            return IpListEntryDto(
                id = entry.id,
                ip_address = entry.ipAddress,
                list_type = entry.listType.name,
                reason = entry.reason,
                expires_at = entry.expiresAt,
                added_by_user_id = entry.addedByUserId,
                created_at = entry.createdAt
            )
        }
    }
}

data class SecurityEventsResponse(
    val events: List<SecurityEventDto>,
    val count: Long,
    val offset: Int,
    val limit: Int
)

data class SecurityConfigResponse(
    val config: SecurityConfigDto
)

data class SecurityConfigDto(
    val block_vpn: Boolean,
    val block_proxy: Boolean,
    val block_datacenter: Boolean,
    val block_tor: Boolean,
    val block_bots: Boolean,
    val fraud_score_threshold: Int,
    val session_timeout_minutes: Int,
    val max_sessions_per_user: Int,
    val ipqs_enabled: Boolean,
    val require_allowlist: Boolean
) {
    companion object {
        fun from(settings: SecuritySettings): SecurityConfigDto {
            return SecurityConfigDto(
                block_vpn = settings.blockVpn,
                block_proxy = settings.blockProxy,
                block_datacenter = settings.blockDatacenter,
                block_tor = settings.blockTor,
                block_bots = settings.blockBots,
                fraud_score_threshold = settings.fraudScoreThreshold,
                session_timeout_minutes = settings.sessionTimeoutMinutes,
                max_sessions_per_user = settings.maxSessionsPerUser,
                ipqs_enabled = settings.ipqsEnabled,
                require_allowlist = settings.requireAllowlist
            )
        }
    }
}

data class SecurityStatsResponse(
    val active_sessions: Long,
    val blocked_attempts_24h: Long,
    val unresolved_events: Long,
    val vpn_flagged_24h: Long,
    val proxy_flagged_24h: Long
) {
    companion object {
        fun from(stats: SecurityStats): SecurityStatsResponse {
            return SecurityStatsResponse(
                active_sessions = stats.activeSessions,
                blocked_attempts_24h = stats.blockedAttempts24h,
                unresolved_events = stats.unresolvedEvents,
                vpn_flagged_24h = stats.vpnFlagged24h,
                proxy_flagged_24h = stats.proxyFlagged24h
            )
        }
    }
}

// ============================================================================
// Request DTOs
// ============================================================================

data class AddIpToListRequest(
    val ip_address: String,
    val list_type: String,
    val reason: String? = null,
    val expires_at: Instant? = null
)

data class RevokeSessionRequest(
    val reason: String? = null
)

data class ResolveEventRequest(
    val notes: String? = null
)

data class BulkResolveEventsRequest(
    val eventIds: List<String>,
    val notes: String? = null
)

data class BulkResolveResponse(
    val resolvedCount: Int,
    val failedIds: List<String>
)

data class UpdateSecurityConfigRequest(
    val block_vpn: Boolean? = null,
    val block_proxy: Boolean? = null,
    val block_datacenter: Boolean? = null,
    val block_tor: Boolean? = null,
    val block_bots: Boolean? = null,
    val fraud_score_threshold: Int? = null,
    val session_timeout_minutes: Int? = null,
    val max_sessions_per_user: Int? = null,
    val ipqs_enabled: Boolean? = null,
    val require_allowlist: Boolean? = null
)

// ============================================================================
// Controller
// ============================================================================

@RestController
@RequestMapping("/admin/security")
@Tag(name = "Admin Security", description = "IP security and session management endpoints")
class AdminSecurityController(
    private val sessionTrackingService: SessionTrackingService,
    private val ipIntelligenceService: IpIntelligenceService,
    private val ipListEntryRepository: IpListEntryRepository,
    private val securityEventRepository: SecurityEventRepository,
    private val securitySettingsRepository: SecuritySettingsRepository
) {

    // ========================================================================
    // Sessions
    // ========================================================================

    @Operation(summary = "List active sessions")
    @GetMapping("/sessions")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun listActiveSessions(): ResponseEntity<ActiveSessionsResponse> {
        logger.info { "GET /admin/security/sessions" }

        val sessions = sessionTrackingService.getActiveSessions()
        return ResponseEntity.ok(ActiveSessionsResponse(
            sessions = sessions.map { SessionDto.from(it) },
            count = sessions.size
        ))
    }

    @Operation(summary = "Revoke a session")
    @DeleteMapping("/sessions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun revokeSession(
        @PathVariable id: String,
        @RequestBody(required = false) request: RevokeSessionRequest?,
        @AuthenticationPrincipal userContext: UserContext
    ): ResponseEntity<Any> {
        logger.info { "DELETE /admin/security/sessions/$id by ${userContext.email}" }

        val session = sessionTrackingService.revokeSession(id, userContext.userId, request?.reason)
        return if (session != null) {
            ResponseEntity.ok(mapOf(
                "id" to id,
                "revoked" to true,
                "message" to "Session revoked successfully"
            ))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @Operation(summary = "Revoke all sessions for a user")
    @DeleteMapping("/sessions/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun revokeAllUserSessions(
        @PathVariable userId: String,
        @RequestBody(required = false) request: RevokeSessionRequest?,
        @AuthenticationPrincipal userContext: UserContext
    ): ResponseEntity<Any> {
        logger.info { "DELETE /admin/security/sessions/user/$userId by ${userContext.email}" }

        val count = sessionTrackingService.revokeAllUserSessions(userId, userContext.userId, request?.reason)
        return ResponseEntity.ok(mapOf(
            "userId" to userId,
            "revokedCount" to count,
            "message" to "Revoked $count sessions"
        ))
    }

    // ========================================================================
    // IP List
    // ========================================================================

    @Operation(summary = "Get IP list entries")
    @GetMapping("/ip-list")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun getIpList(
        @RequestParam(required = false) list_type: String?
    ): ResponseEntity<IpListResponse> {
        logger.info { "GET /admin/security/ip-list - list_type=$list_type" }

        val entries = if (list_type != null) {
            val type = IpListType.valueOf(list_type.uppercase())
            ipListEntryRepository.findByListTypeAndDeletedAtIsNull(type)
        } else {
            ipListEntryRepository.findByDeletedAtIsNull()
        }

        return ResponseEntity.ok(IpListResponse(
            entries = entries.map { IpListEntryDto.from(it) },
            count = entries.size
        ))
    }

    @Operation(summary = "Add IP to list")
    @PostMapping("/ip-list")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun addIpToList(
        @RequestBody request: AddIpToListRequest,
        @AuthenticationPrincipal userContext: UserContext
    ): ResponseEntity<Any> {
        logger.info { "POST /admin/security/ip-list - ip=${request.ip_address}, type=${request.list_type}" }

        val listType = try {
            IpListType.valueOf(request.list_type.uppercase())
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "Invalid list_type. Must be ALLOWLIST or BLOCKLIST"
            ))
        }

        // Check if entry already exists
        val existing = ipListEntryRepository.findByIpAddressAndListTypeAndDeletedAtIsNull(request.ip_address, listType)
        if (existing != null) {
            return ResponseEntity.badRequest().body(mapOf(
                "message" to "IP ${request.ip_address} is already in ${listType.name}"
            ))
        }

        val entry = IpListEntry().apply {
            ipAddress = request.ip_address
            this.listType = listType
            reason = request.reason
            expiresAt = request.expires_at
            addedByUserId = userContext.userId
        }

        val saved = ipListEntryRepository.save(entry)

        // Invalidate IP cache
        ipIntelligenceService.invalidateCache(request.ip_address)

        // Log security event
        val event = SecurityEvent().apply {
            eventType = SecurityEventType.IP_LIST_ADDED
            severity = EventSeverity.LOW
            ipAddress = request.ip_address
            userId = userContext.userId
            userEmail = userContext.email
            title = "IP added to ${listType.name}"
            description = request.reason ?: "IP ${request.ip_address} added to ${listType.name}"
        }
        securityEventRepository.save(event)

        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf(
            "entry" to IpListEntryDto.from(saved)
        ))
    }

    @Operation(summary = "Remove IP from list")
    @DeleteMapping("/ip-list/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun removeIpFromList(
        @PathVariable id: String,
        @AuthenticationPrincipal userContext: UserContext
    ): ResponseEntity<Any> {
        logger.info { "DELETE /admin/security/ip-list/$id by ${userContext.email}" }

        val entry = ipListEntryRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        entry.softDelete(userContext.userId)
        ipListEntryRepository.save(entry)

        // Invalidate IP cache
        ipIntelligenceService.invalidateCache(entry.ipAddress)

        // Log security event
        val event = SecurityEvent().apply {
            eventType = SecurityEventType.IP_LIST_REMOVED
            severity = EventSeverity.LOW
            ipAddress = entry.ipAddress
            userId = userContext.userId
            userEmail = userContext.email
            title = "IP removed from ${entry.listType.name}"
            description = "IP ${entry.ipAddress} removed from ${entry.listType.name}"
        }
        securityEventRepository.save(event)

        return ResponseEntity.ok(mapOf(
            "id" to id,
            "deleted" to true
        ))
    }

    // ========================================================================
    // Security Events
    // ========================================================================

    @Operation(summary = "Get security events")
    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun getSecurityEvents(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(required = false) event_type: String?,
        @RequestParam(required = false) severity: String?,
        @RequestParam(required = false) resolved: Boolean?
    ): ResponseEntity<SecurityEventsResponse> {
        logger.info { "GET /admin/security/events - limit=$limit, offset=$offset" }

        val eventType = event_type?.let { SecurityEventType.valueOf(it.uppercase()) }
        val severityEnum = severity?.let { EventSeverity.valueOf(it.uppercase()) }

        val pageable = PageRequest.of(
            offset / limit.coerceAtLeast(1),
            limit.coerceAtMost(100),
            Sort.by(Sort.Direction.DESC, "createdAt")
        )

        val page = securityEventRepository.findByFilters(eventType, severityEnum, resolved, pageable)

        return ResponseEntity.ok(SecurityEventsResponse(
            events = page.content.map { SecurityEventDto.from(it) },
            count = page.totalElements,
            offset = offset,
            limit = limit
        ))
    }

    @Operation(summary = "Resolve a security event")
    @PostMapping("/events/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun resolveSecurityEvent(
        @PathVariable id: String,
        @RequestBody(required = false) request: ResolveEventRequest?,
        @AuthenticationPrincipal userContext: UserContext
    ): ResponseEntity<Any> {
        logger.info { "POST /admin/security/events/$id/resolve by ${userContext.email}" }

        val event = securityEventRepository.findByIdAndDeletedAtIsNull(id)
            ?: return ResponseEntity.notFound().build()

        event.resolve(userContext.userId, request?.notes)
        val saved = securityEventRepository.save(event)

        return ResponseEntity.ok(mapOf(
            "event" to SecurityEventDto.from(saved)
        ))
    }

    @Operation(summary = "Bulk resolve security events")
    @PostMapping("/events/bulk-resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun bulkResolveSecurityEvents(
        @RequestBody request: BulkResolveEventsRequest,
        @AuthenticationPrincipal userContext: UserContext
    ): ResponseEntity<BulkResolveResponse> {
        logger.info { "POST /admin/security/events/bulk-resolve - ${request.eventIds.size} events by ${userContext.email}" }

        var resolvedCount = 0
        val failedIds = mutableListOf<String>()

        request.eventIds.forEach { id ->
            val event = securityEventRepository.findByIdAndDeletedAtIsNull(id)
            if (event != null && !event.resolved) {
                event.resolve(userContext.userId, request.notes)
                securityEventRepository.save(event)
                resolvedCount++
            } else {
                failedIds.add(id)
            }
        }

        return ResponseEntity.ok(BulkResolveResponse(
            resolvedCount = resolvedCount,
            failedIds = failedIds
        ))
    }

    // ========================================================================
    // Security Config
    // ========================================================================

    @Operation(summary = "Get security configuration")
    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun getSecurityConfig(): ResponseEntity<SecurityConfigResponse> {
        logger.info { "GET /admin/security/config" }

        val settings = securitySettingsRepository.findByIdAndDeletedAtIsNull(SecuritySettings.DEFAULT_ID)
            ?: SecuritySettings.createDefault()

        return ResponseEntity.ok(SecurityConfigResponse(
            config = SecurityConfigDto.from(settings)
        ))
    }

    @Operation(summary = "Update security configuration")
    @PutMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateSecurityConfig(
        @RequestBody request: UpdateSecurityConfigRequest,
        @AuthenticationPrincipal userContext: UserContext
    ): ResponseEntity<SecurityConfigResponse> {
        logger.info { "PUT /admin/security/config by ${userContext.email}" }

        var settings = securitySettingsRepository.findByIdAndDeletedAtIsNull(SecuritySettings.DEFAULT_ID)
        if (settings == null) {
            settings = SecuritySettings.createDefault()
        }

        request.block_vpn?.let { settings.blockVpn = it }
        request.block_proxy?.let { settings.blockProxy = it }
        request.block_datacenter?.let { settings.blockDatacenter = it }
        request.block_tor?.let { settings.blockTor = it }
        request.block_bots?.let { settings.blockBots = it }
        request.fraud_score_threshold?.let { settings.fraudScoreThreshold = it }
        request.session_timeout_minutes?.let { settings.sessionTimeoutMinutes = it }
        request.max_sessions_per_user?.let { settings.maxSessionsPerUser = it }
        request.ipqs_enabled?.let { settings.ipqsEnabled = it }
        request.require_allowlist?.let { settings.requireAllowlist = it }

        val saved = securitySettingsRepository.save(settings)

        // Log config change event
        val event = SecurityEvent().apply {
            eventType = SecurityEventType.CONFIG_CHANGED
            severity = EventSeverity.MEDIUM
            userId = userContext.userId
            userEmail = userContext.email
            title = "Security config updated"
            description = "Security configuration updated by ${userContext.email}"
        }
        securityEventRepository.save(event)

        return ResponseEntity.ok(SecurityConfigResponse(
            config = SecurityConfigDto.from(saved)
        ))
    }

    // ========================================================================
    // Stats
    // ========================================================================

    @Operation(summary = "Get security dashboard statistics")
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    fun getSecurityStats(): ResponseEntity<SecurityStatsResponse> {
        logger.info { "GET /admin/security/stats" }

        val stats = sessionTrackingService.getSecurityStats()
        return ResponseEntity.ok(SecurityStatsResponse.from(stats))
    }
}
