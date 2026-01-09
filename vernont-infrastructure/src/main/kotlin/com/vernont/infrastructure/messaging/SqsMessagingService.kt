package com.vernont.infrastructure.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

private val logger = KotlinLogging.logger {}

/**
 * AWS SQS implementation of MessagingService.
 * Used in production environment.
 */
@Service
@ConditionalOnProperty(name = ["messaging.provider"], havingValue = "sqs")
class SqsMessagingService(
    private val sqsClient: SqsClient,
    private val objectMapper: ObjectMapper,
    private val messagingProperties: MessagingProperties
) : MessagingService {

    override fun publish(topic: String, key: String, event: Any) {
        try {
            val payload = objectMapper.writeValueAsString(event)
            publishRaw(topic, key, payload)
        } catch (e: Exception) {
            logger.error(e) { "Failed to serialize event for queue $topic: ${e.message}" }
            throw e
        }
    }

    override fun publishRaw(topic: String, key: String?, payload: String) {
        try {
            val queueUrl = getQueueUrl(topic)

            val requestBuilder = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(payload)

            // Add message group ID for FIFO queues (based on key)
            if (queueUrl.endsWith(".fifo") && key != null) {
                requestBuilder.messageGroupId(key)
                requestBuilder.messageDeduplicationId("${key}-${System.currentTimeMillis()}")
            }

            // Add message attributes
            if (key != null) {
                requestBuilder.messageAttributes(
                    mapOf(
                        "partitionKey" to MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(key)
                            .build()
                    )
                )
            }

            val response = sqsClient.sendMessage(requestBuilder.build())
            logger.debug { "Published to SQS queue $topic, messageId=${response.messageId()}" }

        } catch (e: Exception) {
            logger.error(e) { "Error publishing to SQS queue $topic: ${e.message}" }
            throw e
        }
    }

    private fun getQueueUrl(topic: String): String {
        // Map topic names to SQS queue URLs from configuration
        return messagingProperties.getEffectiveQueueUrls()[topic]
            ?: throw IllegalArgumentException("No SQS queue URL configured for topic: $topic")
    }
}
