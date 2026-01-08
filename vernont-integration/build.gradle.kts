plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux:4.0.1")
    implementation("org.springframework.boot:spring-boot-starter-data-redis:4.0.1")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.13")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")
    api("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.18.2")
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("io.github.resilience4j:resilience4j-ratelimiter:2.2.0")
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")
    implementation("io.github.resilience4j:resilience4j-kotlin:2.2.0")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.getByName("bootJar") {
    enabled = false
}

tasks.getByName("bootRun") {
    enabled = false
}

tasks.test {
    useJUnitPlatform()
}
