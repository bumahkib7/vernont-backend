package com.vernont.infrastructure.messaging

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.MessageAttributeValue
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * AWS SQS implementation of MessagingService.
 * Uses AWS SDK v1 with AmazonSQS client.
 */
@Service
@ConditionalOnProperty(name = ["messaging.provider"], havingValue = "sqs")
class SqsMessagingService(
    private val amazonSQSClient: AmazonSQS,
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

            val request = SendMessageRequest()
                .withQueueUrl(queueUrl)
                .withMessageBody(payload)

            // Add message group ID for FIFO queues (based on key)
            if (queueUrl.endsWith(".fifo") && key != null) {
                request.withMessageGroupId(key)
                request.withMessageDeduplicationId("${key}-${System.currentTimeMillis()}")
            }

            // Add message attributes
            if (key != null) {
                request.withMessageAttributes(
                    mapOf(
                        "partitionKey" to MessageAttributeValue()
                            .withDataType("String")
                            .withStringValue(key)
                    )
                )
            }

            val result = amazonSQSClient.sendMessage(request)
            logger.debug { "Published to SQS queue $topic, messageId=${result.messageId}" }

        } catch (e: Exception) {
            logger.error(e) { "Error publishing to SQS queue $topic: ${e.message}" }
            throw e
        }
    }

    private fun getQueueUrl(topic: String): String {
        // Map topic names to SQS queue URLs from configuration
        return messagingProperties.getEffectiveQueueUrls()[topic]
            ?: amazonSQSClient.getQueueUrl(topic).queueUrl
    }
}
