package com.vernont.infrastructure.messaging

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Regions
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private val logger = KotlinLogging.logger {}

/**
 * AWS SQS configuration for production environment.
 * Uses AWS SDK v1 with AmazonSQS client.
 */
@Configuration
@ConditionalOnProperty(name = ["messaging.provider"], havingValue = "sqs")
@EnableConfigurationProperties(MessagingProperties::class)
class SqsConfig(
    @Value("\${aws.credentials.access-key}")
    private val accessKey: String,
    @Value("\${aws.credentials.secret-key}")
    private val secretKey: String,
    @Value("\${messaging.sqs.region:eu-north-1}")
    private val region: String
) {

    @Bean
    fun amazonSQSClient(): AmazonSQS {
        logger.info { "Configuring AmazonSQS client for region: $region" }

        val awsCredentials = BasicAWSCredentials(accessKey, secretKey)

        return AmazonSQSClientBuilder.standard()
            .withCredentials(AWSStaticCredentialsProvider(awsCredentials))
            .withRegion(Regions.fromName(region))
            .build()
    }
}
