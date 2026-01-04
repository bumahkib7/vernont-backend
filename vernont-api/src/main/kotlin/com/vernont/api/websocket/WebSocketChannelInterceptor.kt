package com.vernont.api.websocket

import com.vernont.domain.auth.Role
import com.vernont.domain.auth.UserContext
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

@Component
class WebSocketChannelInterceptor : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)

        if (accessor != null && StompCommand.SUBSCRIBE == accessor.command) {
            val destination = accessor.destination
            val sessionAttributes = accessor.sessionAttributes

            if (destination != null && sessionAttributes != null) {
                val userContext = sessionAttributes["userContext"] as? UserContext

                // Protect admin-only destinations
                if (destination.startsWith("/topic/auditlog") ||
                    destination.startsWith("/topic/dashboard") ||
                    destination.startsWith("/queue/admin")) {

                    if (userContext == null || !userContext.roles.contains(Role.ADMIN)) {
                        logger.warn { "SECURITY: Unauthorized subscribe attempt to $destination from ${userContext?.email ?: "unknown"}" }
                        return null // Block the message
                    }
                }

                // Protect user-specific queues (e.g., /queue/user/{userId})
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
