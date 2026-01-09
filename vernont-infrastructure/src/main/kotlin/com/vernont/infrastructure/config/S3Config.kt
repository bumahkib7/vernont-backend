package com.vernont.infrastructure.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

private val logger = KotlinLogging.logger {}

@Configuration
class S3Config(
    private val awsProperties: AwsProperties
) {

    /**
     * Check if using local development (MinIO) based on endpoint URL containing localhost
     */
    private fun isLocalDev(): Boolean {
        val endpoint = awsProperties.s3.endpointUrl
        return endpoint.isNotBlank() && (endpoint.contains("localhost") || endpoint.contains("minio"))
    }

    /**
     * Determines the credentials provider to use:
     * - For local dev (MinIO): uses static credentials when endpoint contains localhost/minio
     * - For production (AWS): uses static credentials from env vars if provided
     */
    private fun getCredentialsProvider(): AwsCredentialsProvider {
        val hasCredentials = awsProperties.credentials.accessKey.isNotBlank() &&
                awsProperties.credentials.secretKey.isNotBlank()

        return if (hasCredentials) {
            if (isLocalDev()) {
                logger.info { "Using static credentials for MinIO (endpoint: ${awsProperties.s3.endpointUrl})" }
            } else {
                logger.info { "Using static credentials for AWS S3 (region: ${awsProperties.s3.region})" }
            }
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(awsProperties.credentials.accessKey, awsProperties.credentials.secretKey)
            )
        } else {
            logger.info { "Using default AWS credentials provider (IAM role/instance profile)" }
            DefaultCredentialsProvider.create()
        }
    }

    @Bean
    fun s3Client(): S3Client {
        val region = Region.of(awsProperties.s3.region)
        val credentialsProvider = getCredentialsProvider()

        val s3ClientBuilder = S3Client.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)

        // Configure endpoint only for local dev (MinIO)
        if (isLocalDev()) {
            s3ClientBuilder
                .endpointOverride(URI.create(awsProperties.s3.endpointUrl))
                .forcePathStyle(true) // Required for S3-compatible services like MinIO
        }

        return s3ClientBuilder.build()
    }

    @Bean
    fun s3Presigner(): S3Presigner {
        val region = Region.of(awsProperties.s3.region)
        val credentialsProvider = getCredentialsProvider()

        val builder = S3Presigner.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)

        if (isLocalDev()) {
            builder.endpointOverride(URI.create(awsProperties.s3.endpointUrl))
        }

        return builder.build()
    }
}
