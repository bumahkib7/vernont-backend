package com.vernont.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "aws")
class AwsProperties {
    var s3: S3 = S3()
    var credentials: Credentials = Credentials()

    class S3 {
        var bucketName: String = ""
        var region: String = "eu-north-1"
        var urlExpirationHours: Int = 24
        var endpointUrl: String = ""
        var presignEnabled: Boolean = false
    }

    class Credentials {
        var accessKey: String = ""
        var secretKey: String = ""
    }
}
