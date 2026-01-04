package com.vernont.infrastructure.audit

import com.vernont.domain.common.BaseEntity
import jakarta.persistence.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * JPA Entity Listener for automatic audit logging
 *
 * Automatically captures entity lifecycle events:
 * - @PostPersist - After entity creation
 * - @PostUpdate - After entity update
 * - @PostRemove - After entity deletion
 *
 * Usage:
 * Add @EntityListeners(EntityAuditListener::class) to entities that need audit logging
 *
 * Note: This is separate from BaseEntity's AuditingEntityListener which handles
 * createdBy/updatedBy fields. This listener handles detailed change logging to AuditLog table.
 */
@Component
class EntityAuditListener {

    private val logger = LoggerFactory.getLogger(EntityAuditListener::class.java)

    companion object {
        // Static reference to AuditService (injected via Spring)
        private var auditService: AuditService? = null

        @Autowired
        fun setAuditService(@Lazy auditService: AuditService) {
            Companion.auditService = auditService
        }
    }

    /**
     * Called after entity is persisted to database
     */
    @PostPersist
    fun afterCreate(entity: Any) {
        if (entity is BaseEntity) {
            try {
                auditService?.logCreate(
                    entity = entity,
                    description = "Entity created: ${entity.javaClass.simpleName}"
                )
                logger.debug("Audit logged for created entity: ${entity.javaClass.simpleName}:${entity.id}")
            } catch (e: Exception) {
                logger.error("Failed to log audit for created entity: ${entity.javaClass.simpleName}:${entity.id}", e)
            }
        }
    }

    /**
     * Called after entity is updated in database
     *
     * Note: We don't have access to old values here, so services should call
     * auditService.logUpdate() manually with both old and new values for detailed change tracking
     */
    @PostUpdate
    fun afterUpdate(entity: Any) {
        if (entity is BaseEntity) {
            try {
                // For automatic logging without old values
                // Services should call auditService.logUpdate() with old values for full diff
                logger.debug("Entity updated: ${entity.javaClass.simpleName}:${entity.id}")
            } catch (e: Exception) {
                logger.error("Failed to process update for entity: ${entity.javaClass.simpleName}", e)
            }
        }
    }

    /**
     * Called after entity is removed from database
     */
    @PostRemove
    fun afterDelete(entity: Any) {
        if (entity is BaseEntity) {
            try {
                auditService?.logDelete(
                    entity = entity,
                    description = "Entity deleted: ${entity.javaClass.simpleName}"
                )
                logger.debug("Audit logged for deleted entity: ${entity.javaClass.simpleName}:${entity.id}")
            } catch (e: Exception) {
                logger.error("Failed to log audit for deleted entity: ${entity.javaClass.simpleName}", e)
            }
        }
    }
}
