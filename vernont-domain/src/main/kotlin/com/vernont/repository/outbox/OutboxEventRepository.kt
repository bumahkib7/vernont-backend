package com.vernont.repository.outbox

import com.vernont.domain.outbox.OutboxEvent
import com.vernont.domain.outbox.OutboxEventStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface OutboxEventRepository : JpaRepository<OutboxEvent, String> {

    /**
     * Find pending events ready for publish (status=PENDING and next_attempt_at <= now)
     */
    @Query("""
        SELECT e FROM OutboxEvent e
        WHERE e.status = :status
        AND e.nextAttemptAt <= :now
        ORDER BY e.createdAt ASC
    """)
    fun findPendingEvents(
        @Param("status") status: OutboxEventStatus = OutboxEventStatus.PENDING,
        @Param("now") now: Instant = Instant.now()
    ): List<OutboxEvent>

    /**
     * Find pending events with limit for batch processing
     */
    @Query("""
        SELECT e FROM OutboxEvent e
        WHERE e.status = 'PENDING'
        AND e.nextAttemptAt <= :now
        ORDER BY e.createdAt ASC
        LIMIT :limit
    """)
    fun findPendingEventsBatch(
        @Param("now") now: Instant,
        @Param("limit") limit: Int
    ): List<OutboxEvent>

    /**
     * Find failed events for alerting/monitoring
     */
    fun findByStatus(status: OutboxEventStatus): List<OutboxEvent>

    /**
     * Find events by aggregate for debugging
     */
    fun findByAggregateIdOrderByCreatedAtDesc(aggregateId: String): List<OutboxEvent>

    /**
     * Find events by type and time range for monitoring
     */
    @Query("""
        SELECT e FROM OutboxEvent e
        WHERE e.eventType = :eventType
        AND e.createdAt >= :since
        ORDER BY e.createdAt DESC
    """)
    fun findByEventTypeSince(
        @Param("eventType") eventType: String,
        @Param("since") since: Instant
    ): List<OutboxEvent>

    /**
     * Count pending events (for metrics)
     */
    fun countByStatus(status: OutboxEventStatus): Long

    /**
     * Delete old published events (cleanup)
     */
    @Query("DELETE FROM OutboxEvent e WHERE e.status = 'PUBLISHED' AND e.publishedAt < :before")
    fun deletePublishedBefore(@Param("before") before: Instant): Int
}
