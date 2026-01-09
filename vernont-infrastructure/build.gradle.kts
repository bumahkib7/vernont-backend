plugins {
    kotlin("plugin.spring")
}

dependencies {
    // Project dependencies
    implementation(project(":vernont-domain"))

    // Spring Boot Web
    implementation("org.springframework.boot:spring-boot-starter-web:4.0.1")

    // Spring Data JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa:4.0.1")

    // Spring WebFlux (for WebClient)
    implementation("org.springframework.boot:spring-boot-starter-webflux:4.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.3")

    // Resilience4j for circuit breaker and retry
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("io.github.resilience4j:resilience4j-reactor:2.2.0")

    // Spring Security
    implementation("org.springframework.boot:spring-boot-starter-security:4.0.1")
    implementation("org.springframework.security:spring-security-crypto:6.4.5")
    implementation("de.mkammerer:argon2-jvm:2.11")

    // Spring Data Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis:4.0.1")

    // Spring WebSocket
    implementation("org.springframework.boot:spring-boot-starter-websocket:4.0.1")
    implementation("org.springframework:spring-aop:7.0.0")
    implementation("org.aspectj:aspectjweaver:1.9.22")

    // MailerSend Java SDK
// https://mvnrepository.com/artifact/com.mailersend/java-sdk
    implementation("com.mailersend:java-sdk:1.4.1")

    // SendGrid Java SDK (optional, legacy)
    implementation("com.sendgrid:sendgrid-java:4.10.2")

    // AWS SDK S3
    implementation("software.amazon.awssdk:s3:2.25.21")

    // AWS SQS (for production messaging)
    implementation("software.amazon.awssdk:sqs:2.25.21")

    // Spring JMS + Amazon SQS JMS integration
    implementation("org.springframework:spring-jms:6.2.2")
    implementation("com.amazonaws:amazon-sqs-java-messaging-lib:2.1.3")
    implementation("jakarta.jms:jakarta.jms-api:3.1.0")

    // Spring Kafka (for Redpanda in dev)
    implementation("org.springframework.kafka:spring-kafka:4.0.1")

    // Spring Boot Actuator (useful for monitoring)
    implementation("org.springframework.boot:spring-boot-starter-actuator:4.0.1")

    // Jackson for JSON processing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Logging
    implementation("org.springframework.boot:spring-boot-starter-logging:4.0.1")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.13")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test:4.0.1")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.testcontainers:testcontainers:1.19.7")
    testImplementation("org.testcontainers:localstack:1.19.7")
}
