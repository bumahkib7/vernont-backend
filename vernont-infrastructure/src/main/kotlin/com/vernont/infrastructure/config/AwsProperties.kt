package com.vernont.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "aws")
class AwsProperties {
    var s3: S3 = S3()
    var credentials: Credentials = Credentials()

    class S3 {
        var bucketName: String = "product-images"
        var region: String = "us-east-1"
        var urlExpirationHours: Int = 24
        var endpointUrl: String = "" // Added this
        var presignEnabled: Boolean = true
    }

    class Credentials {
        var accessKey: String = "minioadmin" // Dummy default for MinIO
        var secretKey: String = "minioadmin" // Dummy default for MinIO
    }
}
