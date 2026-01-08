package com.vernont.infrastructure.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Kafka/Redpanda implementation of MessagingService.
 * Used in development environment with Redpanda.
 */
@Service
@ConditionalOnProperty(name = ["messaging.provider"], havingValue = "kafka", matchIfMissing = true)
class KafkaMessagingService(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) : MessagingService {

    override fun publish(topic: String, key: String, event: Any) {
        try {
            val payload = objectMapper.writeValueAsString(event)
            publishRaw(topic, key, payload)
        } catch (e: Exception) {
            logger.error(e) { "Failed to serialize event for topic $topic: ${e.message}" }
            throw e
        }
    }

    override fun publishRaw(topic: String, key: String?, payload: String) {
        try {
            val future = if (key != null) {
                kafkaTemplate.send(topic, key, payload)
            } else {
                kafkaTemplate.send(topic, payload)
            }

            future.whenComplete { result, ex ->
                if (ex != null) {
                    logger.error(ex) { "Failed to publish to topic $topic: ${ex.message}" }
                } else {
                    logger.debug {
                        "Published to topic $topic, partition=${result?.recordMetadata?.partition()}, " +
                        "offset=${result?.recordMetadata?.offset()}"
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error publishing to Kafka topic $topic: ${e.message}" }
            throw e
        }
    }
}
