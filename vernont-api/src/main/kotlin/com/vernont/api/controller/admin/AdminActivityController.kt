package com.vernont.api.controller.admin

import com.vernont.api.dto.admin.ActivityDto
import com.vernont.api.dto.admin.ActivityListResponse
import com.vernont.api.dto.admin.isBusinessEvent
import com.vernont.repository.AuditLogRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/admin/activity")
@Tag(name = "Admin Activity", description = "Live activity feed endpoints")
class AdminActivityController(
    private val auditLogRepository: AuditLogRepository
) {

    /**
     * Get recent business activity events.
     * Used for initial load and HTTP polling fallback.
     *
     * @param limit Maximum number of events to return (default 50, max 100)
     * @param since Only return events after this timestamp (ISO-8601)
     */
    @Operation(summary = "Get recent activity events")
    @GetMapping
    fun getRecentActivity(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) since: Instant?
    ): ResponseEntity<ActivityListResponse> {
        logger.debug { "GET /admin/activity - limit=$limit, since=$since" }

        // Fetch more than needed to account for filtering
        val fetchLimit = (limit * 3).coerceIn(1, 300)
        val pageable = PageRequest.of(0, fetchLimit)

        val auditLogsPage = if (since != null) {
            auditLogRepository.findBusinessEventsSince(since, pageable)
        } else {
            auditLogRepository.findBusinessEvents(pageable)
        }

        // Filter to allowed business entities only and convert to DTOs
        val activities = auditLogsPage.content
            .filter { isBusinessEvent(it) }
            .take(limit.coerceIn(1, 100))
            .map { ActivityDto.from(it) }

        return ResponseEntity.ok(ActivityListResponse(
            items = activities,
            count = activities.size,
            hasMore = activities.size >= limit
        ))
    }

    /**
     * Poll for new activity events since a specific timestamp.
     * Returns only events that occurred after the given timestamp.
     *
     * @param since Return events after this timestamp (required)
     * @param limit Maximum number of events to return (default 100)
     */
    @Operation(summary = "Poll for new activity events")
    @GetMapping("/poll")
    fun pollActivity(
        @RequestParam since: Instant,
        @RequestParam(defaultValue = "100") limit: Int
    ): ResponseEntity<ActivityListResponse> {
        logger.debug { "GET /admin/activity/poll - since=$since, limit=$limit" }

        // Fetch more than needed to account for filtering
        val fetchLimit = (limit * 3).coerceIn(1, 300)
        val pageable = PageRequest.of(0, fetchLimit)

        val auditLogsPage = auditLogRepository.findBusinessEventsSince(since, pageable)

        // Filter to allowed business entities only and convert to DTOs
        val activities = auditLogsPage.content
            .filter { isBusinessEvent(it) }
            .take(limit.coerceIn(1, 100))
            .map { ActivityDto.from(it) }

        return ResponseEntity.ok(ActivityListResponse(
            items = activities,
            count = activities.size,
            hasMore = activities.size >= limit
        ))
    }
}
