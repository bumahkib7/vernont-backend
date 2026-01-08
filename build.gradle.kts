import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:11.18.0")
        classpath("org.postgresql:postgresql:42.7.5")
    }
}

plugins {
    kotlin("jvm") version "2.3.0" apply false
    kotlin("plugin.spring") version "2.3.0" apply false
    kotlin("plugin.jpa") version "2.3.0" apply false
    id("org.springframework.boot") version "4.0.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.flywaydb.flyway") version "11.18.0" apply false
    id("pl.allegro.tech.build.axion-release") version "1.18.16"
    `maven-publish`
}

// Axion-release configuration for git-based versioning
scmVersion {
    // Use git tags for versioning
    tag {
        prefix.set("v")
        versionSeparator.set("")
    }

    // Hooks for CI/CD
    hooks {
        // Uncomment to auto-push tags
        // pre("push")
    }
}

allprojects {
    group = "com.vernont"
    version = rootProject.scmVersion.version

    repositories {
        mavenCentral()
    }
}

// Modules to publish as dependencies (exclude vernont-api - it's the application entry point)
val publishableModules = setOf(
    "vernont-core",
    "vernont-domain",
    "vernont-application",
    "vernont-infrastructure",
    "vernont-integration",
    "vernont-workflow",
    "vernont-events"
)

    subprojects {
        apply(plugin = "org.jetbrains.kotlin.jvm")
        apply(plugin = "io.spring.dependency-management")
        apply(plugin = "java-library")

        // Apply maven-publish only to publishable modules
        if (name in publishableModules) {
            apply(plugin = "maven-publish")

            configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("maven") {
                        from(components["java"])

                        pom {
                            name.set(project.name)
                            description.set("Vernont ${project.name} module")
                            url.set("https://github.com/YOUR_ORG/vernont")

                            licenses {
                                license {
                                    name.set("Proprietary")
                                }
                            }
                        }
                    }
                }

                repositories {
                    // AWS CodeArtifact (private Maven repository)
                    maven {
                        name = "CodeArtifact"
                        url = uri("https://vernont-${project.findProperty("aws.account") ?: System.getenv("AWS_ACCOUNT_ID")}.d.codeartifact.${project.findProperty("aws.region") ?: "eu-north-1"}.amazonaws.com/maven/vernont-maven/")
                        credentials {
                            username = "aws"
                            password = project.findProperty("codeartifact.token") as String? ?: System.getenv("CODEARTIFACT_AUTH_TOKEN")
                        }
                    }
                }
            }

            // Include sources jar
            tasks.register<Jar>("sourcesJar") {
                archiveClassifier.set("sources")
                from(project.the<SourceSetContainer>()["main"].allSource)
            }

            artifacts {
                add("archives", tasks["sourcesJar"])
            }
        }

        dependencies {
            // Add common dependencies here
        }

        configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_25
            targetCompatibility = JavaVersion.VERSION_25
        }
    tasks.withType<JavaCompile> {
        sourceCompatibility = "25"
        targetCompatibility = "25"
    }

    tasks.withType<KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.addAll(
                "-Xjsr305=strict",
                "-Xannotation-default-target=param-property"
            )
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
