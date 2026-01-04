package com.vernont.domain.audit

import jakarta.persistence.*
import java.time.Instant
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * Audit Log - Tracks all entity changes for compliance and debugging
 *
 * Captures:
 * - Who made the change (userId, userName)
 * - What was changed (entityType, entityId)
 * - When it happened (timestamp)
 * - What type of change (CREATE, UPDATE, DELETE)
 * - What changed (oldValue, newValue)
 * - Where it came from (ipAddress, userAgent)
 */
@Entity
@Table(
        name = "audit_log",
        indexes =
                [
                        Index(name = "idx_audit_entity", columnList = "entity_type,entity_id"),
                        Index(name = "idx_audit_user", columnList = "user_id"),
                        Index(name = "idx_audit_timestamp", columnList = "timestamp"),
                        Index(name = "idx_audit_action", columnList = "action")]
)
class AuditLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null

    @Column(nullable = false) var timestamp: Instant = Instant.now()

    @Column(nullable = false) var userId: String? = "SYSTEM"

    @Column var userName: String? = "SYSTEM"

    @Column(nullable = false) var entityType: String = ""

    @Column(nullable = false) var entityId: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var action: AuditAction = AuditAction.READ

    @Column(columnDefinition = "TEXT") var oldValue: String? = null

    @Column(columnDefinition = "TEXT") var newValue: String? = null

    @Column var ipAddress: String? = null

    @Column var userAgent: String? = null

    @Column(columnDefinition = "TEXT") var description: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var metadata: MutableMap<String, Any?>? = null

    @Version @Column(nullable = false) var version: Long = 0
}

enum class AuditAction {
    CREATE,
    READ,
    UPDATE,
    DELETE,
    LOGIN,
    LOGOUT,
    LOGIN_FAILED,
    PERMISSION_DENIED
}
