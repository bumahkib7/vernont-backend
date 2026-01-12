package com.vernont.api.websocket

import com.vernont.domain.auth.Role
import com.vernont.domain.auth.UserContext
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.stereotype.Component
import java.security.Principal

private val logger = KotlinLogging.logger {}

/**
 * Simple Principal implementation for WebSocket user identification.
 */
class StompPrincipal(private val userId: String) : Principal {
    override fun getName(): String = userId
}

@Component
class WebSocketChannelInterceptor : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message

        val sessionAttributes = accessor.sessionAttributes

        // On CONNECT, set the user principal for user-specific messaging
        // We need to create a new message with updated headers for the principal to be propagated
        if (StompCommand.CONNECT == accessor.command) {
            val userId = sessionAttributes?.get("userId") as? String
            if (userId != null) {
                // Create a mutable accessor to set the user principal
                val mutableAccessor = StompHeaderAccessor.wrap(message)
                mutableAccessor.user = StompPrincipal(userId)
                logger.info { "WebSocket CONNECT: Set principal for user $userId" }
                // Return new message with updated headers
                return MessageBuilder.createMessage(message.payload, mutableAccessor.messageHeaders)
            }
            return message
        }

        if (StompCommand.SUBSCRIBE == accessor.command) {
            val destination = accessor.destination

            if (destination != null && sessionAttributes != null) {
                val userContext = sessionAttributes["userContext"] as? UserContext

                // Protect admin-only destinations
                if (destination.startsWith("/topic/auditlog") ||
                    destination.startsWith("/topic/dashboard") ||
                    destination.startsWith("/topic/sessions") ||
                    destination.startsWith("/topic/security-events") ||
                    destination.startsWith("/queue/admin")) {

                    val hasAdminRole = userContext?.roles?.any {
                        it in listOf(Role.ADMIN, Role.DEVELOPER)
                    } == true

                    if (userContext == null || !hasAdminRole) {
                        logger.warn { "SECURITY: Unauthorized subscribe attempt to $destination from ${userContext?.email ?: "unknown"}" }
                        return null // Block the message
                    }
                }

                // User-specific queue subscriptions (/user/queue/notifications)
                // These are automatically prefixed with /user/{principal.name} by Spring
                // So /user/queue/notifications becomes /user/{userId}/queue/notifications
                if (destination.startsWith("/user/queue/")) {
                    if (userContext == null) {
                        logger.warn { "SECURITY: Unauthenticated user attempted to subscribe to $destination" }
                        return null
                    }
                    logger.debug { "WebSocket user queue subscribe authorized: user=${userContext.email}, destination=$destination" }
                }

                // Protect legacy user-specific queues (e.g., /queue/user/{userId})
                if (destination.startsWith("/queue/user/")) {
                    val targetUserId = destination.substringAfter("/queue/user/").substringBefore("/")
                    if (userContext == null || userContext.userId != targetUserId) {
                        logger.warn { "SECURITY: User ${userContext?.userId} attempted to subscribe to ${targetUserId}'s queue" }
                        return null // Block the message
                    }
                }

                logger.debug { "WebSocket subscribe authorized: user=${userContext?.email}, destination=$destination" }
            }
        }

        return message
    }
}
