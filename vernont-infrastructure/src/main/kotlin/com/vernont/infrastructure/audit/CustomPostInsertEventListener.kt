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
import org.hibernate.event.spi.PostInsertEvent
import org.hibernate.event.spi.PostInsertEventListener
import org.hibernate.persister.entity.EntityPersister
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class CustomPostInsertEventListener(
    private val objectMapper: ObjectMapper
) : PostInsertEventListener, ApplicationContextAware {

    private var applicationContext: ApplicationContext? = null

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        this.applicationContext = applicationContext
    }

    private fun getAuditLogService(): AuditLogService? {
        return applicationContext?.getBean(AuditLogService::class.java)
    }

    override fun onPostInsert(event: PostInsertEvent) {
        val entity = event.entity
        if (entity is AuditLog) return // Avoid auditing audit logs themselves
        val className = entity.javaClass.name
        if (className == "com.vernont.workflow.domain.WorkflowExecution") return // avoid interfering with workflow execution inserts

        try {
            val entityId = event.id.toString()
            val entityType = event.persister.entityName.substringAfterLast('.') // Get simple class name

            // Defer audit write until after commit to avoid modifying Hibernate action queue during flush
            val serialized = serializeEntityForAudit(entity)
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    try {
                        getAuditLogService()?.log(
                            entityType = entityType,
                            entityId = entityId,
                            action = AuditAction.CREATE,
                            newValue = serialized,
                            description = "Entity created: $entityType with ID $entityId"
                        )
                    } catch (ex: Exception) {
                        logger.error(ex) { "Deferred audit logging failed for $entityType:$entityId (create)" }
                    }
                }
            })
        } catch (e: Exception) {
            logger.error(e) { "Failed to log PostInsert for ${entity.javaClass.simpleName}" }
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
                        // Avoid traversing items to prevent deep recursion during audit serialization
                        "total" to entity.total
                    )
                )
                is CartLineItem -> objectMapper.writeValueAsString(
                    mapOf(
                        "id" to entity.id,
                        // Avoid traversing the full cart object
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
