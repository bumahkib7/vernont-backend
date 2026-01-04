package com.vernont.infrastructure.audit // Updated package

import com.vernont.domain.audit.AuditLog
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service

@Service
class AuditLogWebSocketPublisher(
    private val messagingTemplate: SimpMessagingTemplate
) {
    /**
     * Publishes an AuditLog entry to a WebSocket topic.
     * Frontend clients can subscribe to this topic to receive real-time updates.
     */
    fun publishAuditLog(auditLog: AuditLog) {
        // Send to a public topic. Clients can subscribe to /topic/auditlog
        messagingTemplate.convertAndSend("/topic/auditlog", auditLog)
    }
}
