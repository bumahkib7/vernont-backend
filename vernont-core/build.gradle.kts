plugins {
    kotlin("jvm")
}

group = "com.nexus"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))

    // Kotlin test
    testImplementation(kotlin("test"))

    // JUnit 5 (engine + assertions)
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0") // or any recent 5.x
}

// Make tests run on JUnit 5
tasks.test {
    useJUnitPlatform()
}
