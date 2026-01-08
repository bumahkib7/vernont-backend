plugins {
    kotlin("plugin.spring")
}

dependencies {
    // Project dependencies
    implementation(project(":vernont-domain"))
    implementation(project(":vernont-events"))
    implementation(project(":vernont-integration"))
    implementation(project(":vernont-infrastructure"))
    implementation(project(":vernont-application"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter:4.0.1")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa:4.0.1")
    implementation("org.springframework.boot:spring-boot-starter-web:4.0.1")
    implementation("org.springframework.boot:spring-boot-starter-webflux:4.0.1")
    implementation("org.springframework.boot:spring-boot-starter-data-redis:4.0.1")
    implementation("org.springframework.boot:spring-boot-starter-actuator:4.0.1")
    implementation("org.springframework.boot:spring-boot-starter-websocket:4.0.1")
    implementation("org.springframework:spring-context:7.0.0")
    
    // Security for password encoding
    implementation("org.springframework.security:spring-security-crypto:7.0.0")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")

    // Coroutines for async workflows
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")


    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Metrics
    implementation("io.micrometer:micrometer-core:1.11.0")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test:4.0.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.testcontainers:testcontainers:2.0.2")
    testImplementation("org.testcontainers:postgresql:1.21.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    java.toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
