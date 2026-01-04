package com.vernont.domain.cj

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "cj_advertiser_config")
class CjAdvertiserConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null

    @Column(name = "advertiser_id", nullable = false, unique = true)
    var advertiserId: Long = 0

    @Column(name = "advertiser_name")
    var advertiserName: String? = null

    @Column(name = "feed_url", length = 2048)
    var feedUrl: String? = null

    @Column(name = "feed_format")
    var feedFormat: String = "tsv"

    @Column(name = "compression_type")
    var compressionType: String? = null

    @Column(name = "enabled")
    var enabled: Boolean = true

    @Column(name = "schedule_cron")
    var scheduleCron: String? = null

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
