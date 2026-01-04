package com.vernont.api.websocket

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

@Component
class WebSocketRateLimitInterceptor(
    private val redisTemplate: StringRedisTemplate
) : ChannelInterceptor {

    companion object {
        private const val CONNECTION_LIMIT: Long = 5  // Max 5 connections per user per minute
        private const val MESSAGE_LIMIT: Long = 100  // Max 100 messages per user per minute
        private const val WINDOW_SECONDS = 60L
    }

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)

        if (accessor != null) {
            val sessionAttributes = accessor.sessionAttributes
            val userId = sessionAttributes?.get("userId") as? String

            if (userId != null) {
                // Rate limit connections
                if (StompCommand.CONNECT == accessor.command) {
                    if (!checkRateLimit("ws:conn:$userId", CONNECTION_LIMIT)) {
                        logger.warn { "SECURITY: WebSocket connection rate limit exceeded for user $userId" }
                        return null
                    }
                }

                // Rate limit messages
                if (StompCommand.SEND == accessor.command) {
                    if (!checkRateLimit("ws:msg:$userId", MESSAGE_LIMIT)) {
                        logger.warn { "SECURITY: WebSocket message rate limit exceeded for user $userId" }
                        return null
                    }
                }
            }
        }

        return message
    }

    private fun checkRateLimit(key: String, limit: Long): Boolean {
        return try {
            val ops = redisTemplate.opsForValue()
            val count = ops.increment(key) ?: 1

            if (count == 1L) {
                redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS)
            }

            count <= limit
        } catch (e: Exception) {
            logger.error(e) { "Rate limit check failed for key: $key" }
            true // Fail open
        }
    }
}
