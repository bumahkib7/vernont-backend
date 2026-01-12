plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("com.google.cloud.tools.jib") version "3.5.2"
    id("org.flywaydb.flyway")
}

// Flyway configuration for Gradle tasks (must match application.yml defaults)
flyway {
    url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/vernont"
    user = System.getenv("DATABASE_USER") ?: "postgres"
    password = System.getenv("DATABASE_PASSWORD") ?: "postgres"
    schemas = arrayOf("public")
    locations = arrayOf("filesystem:src/main/resources/db/migration")
    baselineOnMigrate = true
    validateOnMigrate = true
}

dependencies {
    // Project modules
    implementation(project(":vernont-domain"))
    implementation(project(":vernont-application"))
    implementation(project(":vernont-workflow"))
    implementation(project(":vernont-events"))
    implementation(project(":vernont-infrastructure"))
    implementation(project(":vernont-integration"))

    // Spring Boot Web
    implementation("org.springframework.boot:spring-boot-starter-web:4.0.1")
    implementation("org.springframework.boot:spring-boot-starter-webflux:4.0.1")
    implementation("org.springframework.boot:spring-boot-starter-validation:4.0.1")
    implementation("org.springframework.boot:spring-boot-autoconfigure:4.0.1") // Added for CacheAutoConfiguration exclusion

    // Spring Security
    implementation("org.springframework.boot:spring-boot-starter-security:4.0.1")
    implementation("org.springframework.security:spring-security-oauth2-resource-server:7.0.0")
    implementation("org.springframework.security:spring-security-oauth2-jose:7.0.0")


    // JWT
    // https://mvnrepository.com/artifact/io.jsonwebtoken/jjwt-api
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    // Argon2 Password Hashing
    implementation("org.springframework.security:spring-security-crypto:6.4.5")
    implementation("de.mkammerer:argon2-jvm:2.11")
    implementation("org.bouncycastle:bcprov-jdk18on:1.79") // Required by Spring Security Argon2PasswordEncoder

    // Spring Data JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa:4.0.1")

    // JCache for Hibernate second level cache
    implementation("org.hibernate.orm:hibernate-core:7.2.0.CR3")
    implementation("org.hibernate.orm:hibernate-jcache:7.2.0.CR3")
    implementation("org.ehcache:ehcache:3.11.1")

    // Database Migration
    implementation("org.flywaydb:flyway-core:11.18.0")
    implementation("org.flywaydb:flyway-database-postgresql:11.18.0")

    // PostgreSQL
    runtimeOnly("org.postgresql:postgresql")

    // Redis Session
    implementation("org.springframework.boot:spring-boot-starter-data-redis:4.0.1")
    implementation("org.springframework.session:spring-session-data-redis:4.0.1")

    // Spring WebSocket
    implementation("org.springframework.boot:spring-boot-starter-websocket:4.0.1")
    implementation("org.springframework.boot:spring-boot-starter-batch:4.0.1")

    // Spring Kafka (for Redpanda in dev, event consumption)
    implementation("org.springframework.kafka:spring-kafka:4.0.1")


    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // https://mvnrepository.com/artifact/io.github.oshai/kotlin-logging-jvm
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.13")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")


    // OpenAPI/Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    // Monitoring
    implementation("org.springframework.boot:spring-boot-starter-actuator:4.0.1")
    implementation("io.micrometer:micrometer-registry-prometheus:1.14.2")

    // Dotenv support - loads .env files into Spring environment
    implementation("me.paulschwarz:spring-dotenv:4.0.0")
    implementation("io.github.cdimascio:dotenv-java:3.0.0")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test:4.0.1")
    testImplementation("org.springframework.security:spring-security-test:7.0.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")

}

tasks.bootRun {
    jvmArgs = listOf("-Xmx2048m", "-XX:MaxMetaspaceSize=512m")
    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property"
        )
        javaParameters.set(true)
    }
}

jib {
    from {
        // glibc-based JRE to avoid missing native libs
        image = "eclipse-temurin:25-jre-jammy"
        platforms {
            platform {
                os = "linux"
                architecture = "amd64"
            }
        }
    }
    to {
        // default to a local image name; override via JIB_TO_IMAGE if needed
        image = System.getenv("JIB_TO_IMAGE") ?: "vernont-backend:latest"
        tags = setOf("latest", "${project.version}")
    }
    container {
        mainClass = "com.vernont.VernontApplicationKt"
        jvmFlags = listOf(
            "-XX:MaxRAMPercentage=75.0",
            "-Dio.netty.transport.noNative=true",
            "-Duser.timezone=UTC",
            "-Djava.security.egd=file:/dev/./urandom"
        )
        ports = listOf("8080")
        creationTime = "USE_CURRENT_TIMESTAMP"
    }
}
