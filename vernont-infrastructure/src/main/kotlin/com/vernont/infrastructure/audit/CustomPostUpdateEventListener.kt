package com.vernont.infrastructure.audit

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule // Added import
import com.fasterxml.jackson.module.kotlin.KotlinModule // Added import
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vernont.infrastructure.audit.AuditLogService
import com.vernont.domain.audit.AuditAction
import com.vernont.domain.audit.AuditLog
import com.vernont.domain.cart.Cart
import com.vernont.domain.cart.CartLineItem
import com.vernont.domain.common.BaseEntity
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct // Added import
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.hibernate.event.spi.PostUpdateEvent
import org.hibernate.event.spi.PostUpdateEventListener
import org.hibernate.persister.entity.EntityPersister
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class CustomPostUpdateEventListener : PostUpdateEventListener, ApplicationContextAware {

    private var applicationContext: ApplicationContext? = null
    private lateinit var objectMapper: ObjectMapper // Declare as lateinit

    @PostConstruct
    fun init() {
        objectMapper = jacksonObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        this.applicationContext = applicationContext
    }

    private fun getAuditLogService(): AuditLogService? {
        return applicationContext?.getBean(AuditLogService::class.java)
    }

    override fun onPostUpdate(event: PostUpdateEvent) {
        val entity = event.entity
        if (entity is AuditLog) return // Avoid auditing audit logs
        val className = entity.javaClass.name
        if (className == "com.vernont.workflow.domain.WorkflowExecution") return // avoid interfering with workflow execution rows

        try {
            val entityId = event.id.toString()
            val entityType = event.persister.entityName.substringAfterLast('.') // Get simple class name

            // For oldValue, we would need to capture the entity state before the update,
            // which is more complex (requires pre-update listener and state comparison).
            // For now, only logging newValue.
            val serialized = serializeEntityForAudit(entity)
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    try {
                        getAuditLogService()?.log(
                            entityType = entityType,
                            entityId = entityId,
                            action = AuditAction.UPDATE,
                            newValue = serialized,
                            description = "Entity updated: $entityType with ID $entityId"
                        )
                    } catch (ex: Exception) {
                        logger.error(ex) { "Deferred audit logging failed for $entityType:$entityId (update)" }
                    }
                }
            })
        } catch (e: Exception) {
            logger.error(e) { "Failed to log PostUpdate for ${entity.javaClass.simpleName}" }
        }
    }

    override fun requiresPostCommitHandling(persister: EntityPersister): Boolean {
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
