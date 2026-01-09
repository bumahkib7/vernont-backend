package com.vernont.infrastructure.messaging

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for messaging.
 */
@ConfigurationProperties(prefix = "messaging")
data class MessagingProperties(
    /**
     * The messaging provider: "kafka" for Redpanda/Kafka, "sqs" for AWS SQS
     */
    val provider: String = "kafka",

    /**
     * Kafka/Redpanda configuration
     */
    val kafka: KafkaProperties = KafkaProperties(),

    /**
     * SQS configuration
     */
    val sqs: SqsProperties = SqsProperties(),

    /**
     * Map of topic names to SQS queue URLs (used only when provider=sqs)
     * Can be configured via:
     * - messaging.sqs-queue-urls.topic-name=url (YAML/properties)
     * - MESSAGING_SQS_QUEUE_URLS_TOPIC_NAME=url (environment variable)
     */
    val sqsQueueUrls: MutableMap<String, String> = mutableMapOf()
) {
    /**
     * Build effective queue URLs map from both explicit config and individual env vars.
     * Only essential queues (orders, notifications) need to be configured.
     * Non-critical topics (workflow-events, etc.) gracefully degrade if not configured.
     */
    fun getEffectiveQueueUrls(): Map<String, String> {
        val urls = sqsQueueUrls.toMutableMap()

        // Add from individual SQS properties if configured
        sqs.ordersQueueUrl?.let { urls["orders"] = it }
        sqs.notificationsQueueUrl?.let { urls["notifications"] = it }

        return urls
    }
}

data class KafkaProperties(
    val bootstrapServers: String = "localhost:19092",
    val consumerGroupId: String = "vernont-inventory-consumer",
    val autoOffsetReset: String = "earliest",
    val enableAutoCommit: Boolean = false
)

data class SqsProperties(
    val region: String = "eu-west-2",
    val endpoint: String? = null, // For LocalStack in tests
    val maxNumberOfMessages: Int = 10,
    val waitTimeSeconds: Int = 20,
    val visibilityTimeout: Int = 30,

    // Individual queue URLs for essential queues (easier env var binding)
    val ordersQueueUrl: String? = null,
    val notificationsQueueUrl: String? = null
)
