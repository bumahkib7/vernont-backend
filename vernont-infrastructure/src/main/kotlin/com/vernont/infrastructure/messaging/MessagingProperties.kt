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
     */
    val sqsQueueUrls: Map<String, String> = emptyMap()
)

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
    val visibilityTimeout: Int = 30
)
