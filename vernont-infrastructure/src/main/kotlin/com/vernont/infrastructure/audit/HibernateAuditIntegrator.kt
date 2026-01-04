package com.vernont.infrastructure.audit

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.hibernate.event.service.spi.EventListenerRegistry
import org.hibernate.event.spi.EventType
import org.hibernate.internal.SessionFactoryImpl
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Configuration to programmatically register our custom JPA event listeners.
 * This allows listeners to be Spring-managed beans and avoids annotating every entity.
 */
@Component
class HibernateAuditListenerRegistrar(
    private val entityManagerFactory: EntityManagerFactory,
    private val postInsertEventListenerProvider: ObjectProvider<CustomPostInsertEventListener>,
    private val postUpdateEventListenerProvider: ObjectProvider<CustomPostUpdateEventListener>,
    private val postDeleteEventListenerProvider: ObjectProvider<CustomPostDeleteEventListener>
) {

    @PostConstruct
    fun registerListeners() {
        try {
            val sessionFactory = entityManagerFactory.unwrap(SessionFactory::class.java)
            // Use a safer approach to get the service registry
            val registry = if (sessionFactory is SessionFactoryImpl) {
                sessionFactory.serviceRegistry.getService(EventListenerRegistry::class.java)
            } else {
                // For proxy cases, try to get the underlying implementation
                val unwrapped = sessionFactory.unwrap(SessionFactoryImpl::class.java)
                unwrapped.serviceRegistry.getService(EventListenerRegistry::class.java)
            } ?: throw IllegalStateException("EventListenerRegistry not found")

            // Register Post-Insert Event Listener
            postInsertEventListenerProvider.ifAvailable { listener ->
                registry.appendListeners(EventType.POST_INSERT, listener)
                logger.info { "Registered CustomPostInsertEventListener with Hibernate." }
            } ?: logger.warn { "CustomPostInsertEventListener not found in ApplicationContext." }

            // Register Post-Update Event Listener
            postUpdateEventListenerProvider.ifAvailable { listener ->
                registry.appendListeners(EventType.POST_UPDATE, listener)
                logger.info { "Registered CustomPostUpdateEventListener with Hibernate." }
            } ?: logger.warn { "CustomPostUpdateEventListener not found in ApplicationContext." }

            // Register Post-Delete Event Listener
            postDeleteEventListenerProvider.ifAvailable { listener ->
                registry.appendListeners(EventType.POST_DELETE, listener)
                logger.info { "Registered CustomPostDeleteEventListener with Hibernate." }
            } ?: logger.warn { "CustomPostDeleteEventListener not found in ApplicationContext." }
        } catch (e: Exception) {
            logger.error(e) { "Failed to register Hibernate audit listeners: ${e.message}" }
            // Don't rethrow - allow application to start even if audit listeners fail to register
        }
    }
}
