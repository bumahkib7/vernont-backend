plugins {
    kotlin("plugin.spring")
}

dependencies {
    // Project modules
    implementation(project(":vernont-domain"))
    implementation(project(":vernont-integration"))
    implementation(project(":vernont-events"))
    implementation(project(":vernont-infrastructure"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter:4.0.1")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa:4.0.1")
    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch:4.0.1") // Re-enabled
    implementation("org.springframework.boot:spring-boot-starter-websocket:4.0.1")
    implementation("org.springframework:spring-tx:7.0.0")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2")

    // Caching and Resilience
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("io.github.resilience4j:resilience4j-ratelimiter:2.2.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")
    implementation("io.github.resilience4j:resilience4j-kotlin:2.2.0")

    // Elasticsearch client for compatibility with Spring Boot 4.0.1 - Re-enabled
    implementation("co.elastic.clients:elasticsearch-java:9.2.2") {
        exclude(group = "commons-logging", module = "commons-logging")
    }
    implementation("co.elastic.clients:elasticsearch-rest5-client:9.2.2")
    // Elasticsearch REST client - Required for the Java API client
    implementation("org.elasticsearch.client:elasticsearch-rest-client:9.2.2") {
        exclude(group = "commons-logging", module = "commons-logging")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation:4.0.1")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")

    // Metrics
    implementation("io.micrometer:micrometer-core:1.11.0")

    // Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis:4.0.1")

    // Security Crypto (for PasswordEncoder)
    implementation("org.springframework.security:spring-security-crypto:6.4.5")

    // Stripe SDK for payment processing
    implementation("com.stripe:stripe-java:31.2.0-beta.1")

    // Testing
    testImplementation("io.mockk:mockk:1.14.7")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
