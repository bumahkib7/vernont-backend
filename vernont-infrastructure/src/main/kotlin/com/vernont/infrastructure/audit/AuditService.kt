package com.vernont.infrastructure.audit

import com.fasterxml.jackson.databind.ObjectMapper
import com.vernont.domain.audit.AuditAction
import com.vernont.domain.audit.AuditLog
import com.vernont.domain.common.BaseEntity
import com.vernont.repository.AuditLogRepository
import jakarta.servlet.http.HttpServletRequest
import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Service for capturing and persisting audit logs
 *
 * Automatically tracks:
 * - Entity changes (CREATE, UPDATE, DELETE)
 * - Security events (LOGIN, LOGOUT, PERMISSION_DENIED)
 * - User actions with full context
 *
 * Usage:
 * ```
 * auditService.logCreate(product, request)
 * auditService.logUpdate(product, oldProduct, request)
 * auditService.logDelete(product, request)
 * ```
 */
@Service
class AuditService(
        private val auditLogRepository: AuditLogRepository,
        private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(AuditService::class.java)

    /** Log entity creation */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun logCreate(
            entity: BaseEntity,
            request: HttpServletRequest? = null,
            description: String? = null,
            metadata: Map<String, Any?>? = null
    ) {
        try {
            val auditLog =
                    createAuditLog(
                            entityType = entity.javaClass.simpleName,
                            entityId = entity.id,
                            action = AuditAction.CREATE,
                            oldValue = null,
                            newValue = serializeEntity(entity),
                            request = request,
                            description = description,
                            metadata = metadata
                    )

            auditLogRepository.save(auditLog)
            logger.debug("Logged CREATE for ${entity.javaClass.simpleName}:${entity.id}")
        } catch (e: Exception) {
            logger.error(
                    "Failed to log CREATE audit for ${entity.javaClass.simpleName}:${entity.id}",
                    e
            )
        }
    }

    /** Log entity update */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun logUpdate(
            newEntity: BaseEntity,
            oldEntity: BaseEntity,
            request: HttpServletRequest? = null,
            description: String? = null,
            metadata: Map<String, Any?>? = null
    ) {
        try {
            val auditLog =
                    createAuditLog(
                            entityType = newEntity.javaClass.simpleName,
                            entityId = newEntity.id,
                            action = AuditAction.UPDATE,
                            oldValue = serializeEntity(oldEntity),
                            newValue = serializeEntity(newEntity),
                            request = request,
                            description = description,
                            metadata = metadata
                    )

            auditLogRepository.save(auditLog)
            logger.debug("Logged UPDATE for ${newEntity.javaClass.simpleName}:${newEntity.id}")
        } catch (e: Exception) {
            logger.error(
                    "Failed to log UPDATE audit for ${newEntity.javaClass.simpleName}:${newEntity.id}",
                    e
            )
        }
    }

    /** Log entity deletion */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun logDelete(
            entity: BaseEntity,
            request: HttpServletRequest? = null,
            description: String? = null,
            metadata: Map<String, Any?>? = null
    ) {
        try {
            val auditLog =
                    createAuditLog(
                            entityType = entity.javaClass.simpleName,
                            entityId = entity.id,
                            action = AuditAction.DELETE,
                            oldValue = serializeEntity(entity),
                            newValue = null,
                            request = request,
                            description = description,
                            metadata = metadata
                    )

            auditLogRepository.save(auditLog)
            logger.debug("Logged DELETE for ${entity.javaClass.simpleName}:${entity.id}")
        } catch (e: Exception) {
            logger.error(
                    "Failed to log DELETE audit for ${entity.javaClass.simpleName}:${entity.id}",
                    e
            )
        }
    }

    /** Log read operation (optional, use sparingly to avoid performance impact) */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun logRead(
            entity: BaseEntity,
            request: HttpServletRequest? = null,
            description: String? = null
    ) {
        try {
            val auditLog =
                    createAuditLog(
                            entityType = entity.javaClass.simpleName,
                            entityId = entity.id,
                            action = AuditAction.READ,
                            oldValue = null,
                            newValue = null,
                            request = request,
                            description = description
                    )

            auditLogRepository.save(auditLog)
        } catch (e: Exception) {
            logger.error(
                    "Failed to log READ audit for ${entity.javaClass.simpleName}:${entity.id}",
                    e
            )
        }
    }

    /** Log security events (login, logout, permission denied) */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun logSecurityEvent(
            action: AuditAction,
            userId: String? = null,
            userName: String? = null,
            request: HttpServletRequest? = null,
            description: String? = null,
            metadata: Map<String, Any?>? = null
    ) {
        try {
            val auditLog =
                    AuditLog().apply {
                        this.timestamp = Instant.now()
                        this.userId = userId ?: getCurrentUserId()
                        this.userName = userName ?: getCurrentUserName()
                        this.entityType = "SECURITY_EVENT"
                        this.entityId = action.name
                        this.action = action
                        this.ipAddress = getIpAddress(request)
                        this.userAgent = getUserAgent(request)
                        this.description = description
                        this.metadata = metadata?.toMutableMap()
                    }

            auditLogRepository.save(auditLog)
            logger.info("Logged security event: $action for user: ${auditLog.userId}")
        } catch (e: Exception) {
            logger.error("Failed to log security event: $action", e)
        }
    }

    /** Log custom action */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun logCustomAction(
            entityType: String,
            entityId: String,
            action: AuditAction,
            description: String,
            request: HttpServletRequest? = null,
            metadata: Map<String, Any?>? = null
    ) {
        try {
            val auditLog =
                    createAuditLog(
                            entityType = entityType,
                            entityId = entityId,
                            action = action,
                            oldValue = null,
                            newValue = null,
                            request = request,
                            description = description,
                            metadata = metadata
                    )

            auditLogRepository.save(auditLog)
            logger.debug("Logged custom action: {} for {}:{}", action, entityType, entityId)
        } catch (e: Exception) {
            logger.error("Failed to log custom action: $action for $entityType:$entityId", e)
        }
    }

    /** Create audit log entry */
    private fun createAuditLog(
            entityType: String,
            entityId: String,
            action: AuditAction,
            oldValue: String?,
            newValue: String?,
            request: HttpServletRequest?,
            description: String? = null,
            metadata: Map<String, Any?>? = null
    ): AuditLog {
        return AuditLog().apply {
            this.timestamp = Instant.now()
            this.userId = getCurrentUserId()
            this.userName = getCurrentUserName()
            this.entityType = entityType
            this.entityId = entityId
            this.action = action
            this.oldValue = oldValue
            this.newValue = newValue
            this.ipAddress = getIpAddress(request)
            this.userAgent = getUserAgent(request)
            this.description = description
            this.metadata = metadata?.toMutableMap()
        }
    }

    /** Serialize entity to JSON for audit log */
    private fun serializeEntity(entity: BaseEntity): String {
        return try {
            objectMapper.writeValueAsString(entity)
        } catch (e: Exception) {
            logger.warn("Failed to serialize entity ${entity.javaClass.simpleName}:${entity.id}", e)
            "{\"id\": \"${entity.id}\", \"error\": \"Serialization failed\"}"
        }
    }

    /** Get current user ID from security context */
    private fun getCurrentUserId(): String {
        return try {
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication != null && authentication.isAuthenticated) {
                // Ensure name is not null/blank
                authentication.name.takeIf { !it.isNullOrBlank() } ?: "SYSTEM"
            } else {
                "SYSTEM"
            }
        } catch (e: Exception) {
            "SYSTEM"
        }
    }

    /** Get current user name from security context */
    private fun getCurrentUserName(): String {
        return try {
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication != null && authentication.isAuthenticated) {
                // Try to get full name from principal if available
                authentication.name.takeIf { !it.isNullOrBlank() } ?: "SYSTEM"
            } else {
                "SYSTEM"
            }
        } catch (e: Exception) {
            "SYSTEM"
        }
    }

    /** Extract IP address from request */
    private fun getIpAddress(request: HttpServletRequest?): String? {
        if (request == null) return null

        // Check for proxy headers
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        if (xForwardedFor != null) {
            return xForwardedFor.split(",").firstOrNull()?.trim()
        }

        val xRealIp = request.getHeader("X-Real-IP")
        if (xRealIp != null) {
            return xRealIp
        }

        return request.remoteAddr
    }

    /** Extract user agent from request */
    private fun getUserAgent(request: HttpServletRequest?): String? {
        return request?.getHeader("User-Agent")
    }
}
