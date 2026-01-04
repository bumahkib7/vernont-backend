plugins {
    kotlin("plugin.jpa")
    kotlin("plugin.spring")
}

dependencies {
    // Spring Data JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa:4.0.1")
    // Spring Security
    implementation("org.springframework.boot:spring-boot-starter-security:4.0.1")

    // Hibernate
    implementation("org.hibernate.orm:hibernate-core:7.0.0.Final")
    implementation("org.hibernate.orm:hibernate-jpamodelgen:7.0.0.Final")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation:4.0.1")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")

    // PostgreSQL
    // https://mvnrepository.com/artifact/org.postgresql/postgresql
    runtimeOnly("org.postgresql:postgresql:42.7.8")
}

tasks.register<JavaExec>("generateSchema") {
    group = "database"
    description = "Generate SQL schema from JPA entities without a database."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.vernont.tools.SchemaExporterTool")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
    val outputFile = project.findProperty("outputFile")?.toString()
        ?: "${project.rootDir}/vernont-api/src/main/resources/db/migration/V1__baseline.sql"
    args(outputFile)
}
