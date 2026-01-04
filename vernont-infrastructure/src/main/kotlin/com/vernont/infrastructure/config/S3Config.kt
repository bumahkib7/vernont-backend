package com.vernont.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI // Import URI

@Configuration
class S3Config(
    private val awsProperties: AwsProperties
) {

    @Bean
    fun s3Client(): S3Client {
        val region = Region.of(awsProperties.s3.region)
        val credentialsProvider = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(awsProperties.credentials.accessKey, awsProperties.credentials.secretKey)
        )

        val s3ClientBuilder = S3Client.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)

        // Configure endpoint if provided
        if (awsProperties.s3.endpointUrl.isNotBlank()) {
            s3ClientBuilder
                .endpointOverride(URI.create(awsProperties.s3.endpointUrl))
                .forcePathStyle(true) // Often required for S3-compatible services like MinIO
        }

        return s3ClientBuilder.build()
    }

    @Bean
    fun s3Presigner(): S3Presigner {
        val region = Region.of(awsProperties.s3.region)
        val credentialsProvider = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(awsProperties.credentials.accessKey, awsProperties.credentials.secretKey)
        )

        val builder = S3Presigner.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)

        if (awsProperties.s3.endpointUrl.isNotBlank()) {
            builder.endpointOverride(URI.create(awsProperties.s3.endpointUrl))
        }

        return builder.build()
    }
}
