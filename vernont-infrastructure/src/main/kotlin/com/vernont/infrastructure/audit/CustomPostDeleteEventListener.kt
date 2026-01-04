package com.vernont.infrastructure.audit

import com.fasterxml.jackson.databind.ObjectMapper
import com.vernont.infrastructure.audit.AuditLogService // Updated import
import com.vernont.domain.audit.AuditAction
import com.vernont.domain.audit.AuditLog
import com.vernont.domain.cart.Cart
import com.vernont.domain.cart.CartLineItem
import com.vernont.domain.common.BaseEntity
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.hibernate.event.spi.PostDeleteEvent
import org.hibernate.event.spi.PostDeleteEventListener
import org.hibernate.persister.entity.EntityPersister
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class CustomPostDeleteEventListener(
    private val objectMapper: ObjectMapper
) : PostDeleteEventListener, ApplicationContextAware {

    private var applicationContext: ApplicationContext? = null

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        this.applicationContext = applicationContext
    }

    private fun getAuditLogService(): AuditLogService? {
        return applicationContext?.getBean(AuditLogService::class.java)
    }

    override fun onPostDelete(event: PostDeleteEvent) {
        val entity = event.entity
        if (entity is AuditLog) return // Avoid auditing audit logs
        val className = entity.javaClass.name
        if (className == "com.vernont.workflow.domain.WorkflowExecution") return // avoid interfering with workflow execution rows

        try {
            val entityId = event.id.toString()
            val entityType = event.persister.entityName.substringAfterLast('.') // Get simple class name

            // For deleted entities, oldValue can be the entity itself before deletion
            val serialized = serializeEntityForAudit(entity)
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    try {
                        getAuditLogService()?.log(
                            entityType = entityType,
                            entityId = entityId,
                            action = AuditAction.DELETE,
                            oldValue = serialized,
                            description = "Entity deleted: $entityType with ID $entityId"
                        )
                    } catch (ex: Exception) {
                        logger.error(ex) { "Deferred audit logging failed for $entityType:$entityId (delete)" }
                    }
                }
            })
        } catch (e: Exception) {
            logger.error(e) { "Failed to log PostDelete for ${entity.javaClass.simpleName}" }
        }
    }

    override fun requiresPostCommitHandling(persister: EntityPersister): Boolean { // Fixed typo
        return true // Ensure this listener runs after the transaction commits
    }

    private fun serializeEntityForAudit(entity: Any): String? {
        return try {
            when (entity) {
                is Cart -> objectMapper.writeValueAsString(
                    mapOf(
                        "id" to entity.id,
                        "customerId" to entity.customerId,
                        "currencyCode" to entity.currencyCode,
                        "itemIds" to entity.items.mapNotNull { it.id },
                        "total" to entity.total
                    )
                )
                is CartLineItem -> objectMapper.writeValueAsString(
                    mapOf(
                        "id" to entity.id,
                        "cartId" to entity.cart?.id,
                        "variantId" to entity.variantId,
                        "quantity" to entity.quantity,
                        "unitPrice" to entity.unitPrice,
                        "total" to entity.total
                    )
                )
                is BaseEntity -> objectMapper.writeValueAsString(
                    mapOf(
                        "id" to entity.id,
                        "type" to entity.javaClass.simpleName
                    )
                )
                else -> objectMapper.writeValueAsString(entity)
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to serialize entity ${entity.javaClass.simpleName} for audit; falling back to class name" }
            "{\"entity\":\"${entity.javaClass.name}\"}"
        }
    }
}
