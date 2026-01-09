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
     * Determines the credentials provider to use:
     * - For local dev (MinIO): uses static credentials when endpoint is set
     * - For production (AWS): uses DefaultCredentialsProvider (IAM role, env vars, etc.)
     */
    private fun getCredentialsProvider(): AwsCredentialsProvider {
        val useStaticCredentials = awsProperties.s3.endpointUrl.isNotBlank()

        return if (useStaticCredentials) {
            logger.info { "Using static credentials for S3 (endpoint: ${awsProperties.s3.endpointUrl})" }
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(awsProperties.credentials.accessKey, awsProperties.credentials.secretKey)
            )
        } else {
            logger.info { "Using default AWS credentials provider for S3 (IAM role/environment)" }
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

        // Configure endpoint if provided (for MinIO or S3-compatible services)
        if (awsProperties.s3.endpointUrl.isNotBlank()) {
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

        if (awsProperties.s3.endpointUrl.isNotBlank()) {
            builder.endpointOverride(URI.create(awsProperties.s3.endpointUrl))
        }

        return builder.build()
    }
}
