package com.vernont.infrastructure.messaging

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import java.net.URI

private val logger = KotlinLogging.logger {}

/**
 * AWS SQS configuration for production environment.
 */
@Configuration
@ConditionalOnProperty(name = ["messaging.provider"], havingValue = "sqs")
@EnableConfigurationProperties(MessagingProperties::class)
class SqsConfig(
    private val messagingProperties: MessagingProperties
) {

    @Bean
    fun sqsClient(): SqsClient {
        logger.info { "Configuring SQS client for region: ${messagingProperties.sqs.region}" }

        val builder = SqsClient.builder()
            .region(Region.of(messagingProperties.sqs.region))
            .credentialsProvider(DefaultCredentialsProvider.create())

        // Support LocalStack endpoint for testing
        messagingProperties.sqs.endpoint?.let { endpoint ->
            logger.info { "Using custom SQS endpoint: $endpoint" }
            builder.endpointOverride(URI.create(endpoint))
        }

        return builder.build()
    }
}
