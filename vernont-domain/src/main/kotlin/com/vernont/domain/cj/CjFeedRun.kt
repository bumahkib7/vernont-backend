package com.vernont.domain.cj

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(name = "cj_feed_run")
class CjFeedRun {
    @field:Id
    @field:Column(name = "run_id", nullable = false, updatable = false)
    var runId: String = ""

    @field:Column(name = "advertiser_id", nullable = false)
    var advertiserId: Long = 0

    @field:Column(name = "feed_config_id")
    var feedConfigId: Long? = null

    @field:Column(name = "feed_name")
    var feedName: String? = null

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false)
    var status: CjFeedRunStatus = CjFeedRunStatus.STARTED

    @field:Column(name = "processed")
    var processed: Long = 0

    @field:Column(name = "total")
    var total: Long? = null

    @field:Column(name = "started_at", nullable = false)
    var startedAt: Instant = Instant.now()

    @field:Column(name = "finished_at")
    var finishedAt: Instant? = null

    @field:Column(name = "last_updated_at", nullable = false)
    var lastUpdatedAt: Instant = Instant.now()

    @field:CreationTimestamp
    @field:Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @field:UpdateTimestamp
    @field:Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @field:Version
    @field:Column(name = "version", nullable = false)
    var version: Int = 0
}

enum class CjFeedRunStatus {
    STARTED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLING,
    CANCELLED
}
