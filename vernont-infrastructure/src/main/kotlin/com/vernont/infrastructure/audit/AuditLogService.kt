package com.vernont.infrastructure.audit // Updated package

import com.vernont.domain.audit.AuditAction
import com.vernont.domain.audit.AuditLog
import com.vernont.domain.auth.getCurrentUserContext // Updated import
import com.vernont.repository.AuditLogRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Instant
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class AuditLogService(
        private val auditLogRepository: AuditLogRepository,
        private val auditLogWebSocketPublisher: AuditLogWebSocketPublisher,
        private val eventPublisher: ApplicationEventPublisher
) {

    @Transactional
    fun log(
            entityType: String,
            entityId: String,
            action: AuditAction,
            description: String? = null,
            oldValue: String? = null,
            newValue: String? = null,
            metadata: Map<String, Any?>? = null
    ) {
        try {
            val userContext = getCurrentUserContext()
            val userId = userContext?.userId?.takeIf { it.isNotBlank() } ?: "SYSTEM"
            val userName = userContext?.getFullName()?.takeIf { it.isNotBlank() } ?: "SYSTEM"

            val auditLog =
                    AuditLog().apply {
                        this.timestamp = Instant.now()
                        this.userId = userId
                        this.userName = userName
                        this.entityType = entityType
                        this.entityId = entityId
                        this.action = action
                        this.oldValue = oldValue
                        this.newValue = newValue
                        this.description = description
                        // IP Address and User Agent will be handled by a request interceptor if
                        // needed
                        this.metadata = metadata?.toMutableMap()
                    }

            val savedAuditLog = auditLogRepository.save(auditLog)
            auditLogWebSocketPublisher.publishAuditLog(savedAuditLog)

            // Publish Spring event for dashboard updates
            eventPublisher.publishEvent(savedAuditLog)

            logger.debug {
                "AuditLog saved and published: ${savedAuditLog.id} - ${savedAuditLog.action} on ${savedAuditLog.entityType}:${entityId}"
            }
        } catch (e: Exception) {
            logger.error(e) {
                "Failed to create or publish audit log for ${entityType}:${entityId} action ${action}"
            }
        }
    }
}
