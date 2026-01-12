package com.vernont.infrastructure.security

import com.vernont.domain.security.AdminSession
import com.vernont.domain.security.SecurityEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * WebSocket publisher for real-time session and security event updates.
 * Publishes to topics that the admin dashboard can subscribe to.
 */
@Service
class SessionWebSocketPublisher(
    private val messagingTemplate: SimpMessagingTemplate
) {
    companion object {
        const val TOPIC_SESSIONS = "/topic/sessions"
        const val TOPIC_SECURITY_EVENTS = "/topic/security-events"
    }

    /**
     * Publish a session created event.
     */
    fun publishSessionCreated(session: AdminSession) {
        val message = SessionWebSocketMessage(
            type = SessionMessageType.SESSION_CREATED,
            session = SessionDto.from(session)
        )
        messagingTemplate.convertAndSend(TOPIC_SESSIONS, message)
        logger.debug { "Published SESSION_CREATED for session ${session.id}" }
    }

    /**
     * Publish a session updated event (e.g., heartbeat).
     */
    fun publishSessionUpdated(session: AdminSession) {
        val message = SessionWebSocketMessage(
            type = SessionMessageType.SESSION_UPDATED,
            session = SessionDto.from(session)
        )
        messagingTemplate.convertAndSend(TOPIC_SESSIONS, message)
        logger.debug { "Published SESSION_UPDATED for session ${session.id}" }
    }

    /**
     * Publish a session expired event.
     */
    fun publishSessionExpired(session: AdminSession) {
        val message = SessionWebSocketMessage(
            type = SessionMessageType.SESSION_EXPIRED,
            session = SessionDto.from(session)
        )
        messagingTemplate.convertAndSend(TOPIC_SESSIONS, message)
        logger.debug { "Published SESSION_EXPIRED for session ${session.id}" }
    }

    /**
     * Publish a session revoked event.
     */
    fun publishSessionRevoked(session: AdminSession) {
        val message = SessionWebSocketMessage(
            type = SessionMessageType.SESSION_REVOKED,
            session = SessionDto.from(session)
        )
        messagingTemplate.convertAndSend(TOPIC_SESSIONS, message)
        logger.debug { "Published SESSION_REVOKED for session ${session.id}" }
    }

    /**
     * Publish a security event.
     */
    fun publishSecurityEvent(event: SecurityEvent) {
        val message = SecurityEventWebSocketMessage(
            type = event.eventType.name,
            event = SecurityEventDto.from(event)
        )
        messagingTemplate.convertAndSend(TOPIC_SECURITY_EVENTS, message)
        logger.debug { "Published security event ${event.id} of type ${event.eventType}" }
    }
}

enum class SessionMessageType {
    SESSION_CREATED,
    SESSION_UPDATED,
    SESSION_EXPIRED,
    SESSION_REVOKED
}

data class SessionWebSocketMessage(
    val type: SessionMessageType,
    val session: SessionDto,
    val timestamp: Instant = Instant.now()
)

data class SecurityEventWebSocketMessage(
    val type: String,
    val event: SecurityEventDto,
    val timestamp: Instant = Instant.now()
)

data class SessionDto(
    val id: String,
    val userId: String,
    val userEmail: String?,
    val ipAddress: String,
    val deviceType: String?,
    val browser: String?,
    val browserVersion: String?,
    val os: String?,
    val osVersion: String?,
    val countryCode: String?,
    val city: String?,
    val latitude: Double?,
    val longitude: Double?,
    val status: String,
    val lastActivityAt: Instant,
    val flaggedVpn: Boolean,
    val flaggedProxy: Boolean,
    val fraudScore: Int?,
    val createdAt: Instant
) {
    companion object {
        fun from(session: AdminSession, userEmail: String? = null): SessionDto {
            return SessionDto(
                id = session.id,
                userId = session.userId,
                userEmail = userEmail,
                ipAddress = session.ipAddress,
                deviceType = session.deviceType,
                browser = session.browser,
                browserVersion = session.browserVersion,
                os = session.os,
                osVersion = session.osVersion,
                countryCode = session.countryCode,
                city = session.city,
                latitude = session.latitude,
                longitude = session.longitude,
                status = session.status.name,
                lastActivityAt = session.lastActivityAt,
                flaggedVpn = session.flaggedVpn,
                flaggedProxy = session.flaggedProxy,
                fraudScore = session.fraudScore,
                createdAt = session.createdAt
            )
        }
    }
}

data class SecurityEventDto(
    val id: String,
    val eventType: String,
    val severity: String,
    val ipAddress: String?,
    val userId: String?,
    val userEmail: String?,
    val title: String,
    val description: String?,
    val countryCode: String?,
    val city: String?,
    val fraudScore: Int?,
    val isVpn: Boolean?,
    val isProxy: Boolean?,
    val resolved: Boolean,
    val createdAt: Instant
) {
    companion object {
        fun from(event: SecurityEvent): SecurityEventDto {
            return SecurityEventDto(
                id = event.id,
                eventType = event.eventType.name,
                severity = event.severity.name,
                ipAddress = event.ipAddress,
                userId = event.userId,
                userEmail = event.userEmail,
                title = event.title,
                description = event.description,
                countryCode = event.countryCode,
                city = event.city,
                fraudScore = event.fraudScore,
                isVpn = event.isVpn,
                isProxy = event.isProxy,
                resolved = event.resolved,
                createdAt = event.createdAt
            )
        }
    }
}
