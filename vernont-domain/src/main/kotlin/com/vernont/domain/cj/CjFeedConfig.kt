package com.vernont.domain.cj

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(
    name = "cj_feed_config",
    indexes = [
        Index(name = "idx_cj_feed_advertiser", columnList = "advertiser_id"),
        Index(name = "idx_cj_feed_enabled", columnList = "enabled"),
        Index(name = "idx_cj_feed_name", columnList = "feed_name")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_cj_feed_name", columnNames = ["advertiser_id", "feed_name"])
    ]
)
class CjFeedConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null

    @Column(name = "advertiser_id", nullable = false)
    var advertiserId: Long = 0

    @Column(name = "advertiser_name")
    var advertiserName: String? = null

    @Column(name = "feed_name", nullable = false, length = 512)
    var feedName: String = ""

    @Column(name = "feed_url", length = 2048)
    var feedUrl: String? = null

    @Column(name = "feed_format")
    var feedFormat: String = "tsv"

    @Column(name = "compression_type")
    var compressionType: String? = null

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true

    @Column(name = "language", length = 10)
    var language: String? = null

    @Column(name = "region", length = 10)
    var region: String? = null

    @Column(name = "priority", nullable = false)
    var priority: Int = 100

    @Column(name = "last_sync_at")
    var lastSyncAt: Instant? = null

    @Column(name = "last_sync_status")
    var lastSyncStatus: String? = null

    @Column(name = "last_sync_run_id")
    var lastSyncRunId: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    var metadata: MutableMap<String, Any?>? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @Version
    @Column(name = "version", nullable = false)
    var version: Int = 0
}
