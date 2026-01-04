# syntax=docker/dockerfile:1.6

# -------- BUILD STAGE (Gradle wrapper → Gradle 9.x) --------
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /home/gradle/project

# Gradle wrapper
COPY gradlew ./
COPY gradle/wrapper ./gradle/wrapper
RUN chmod +x gradlew

# Root Gradle files
COPY settings.gradle.kts build.gradle.kts gradle.properties ./

# Module build files
COPY vernont-domain/build.gradle.kts        vernont-domain/
COPY vernont-application/build.gradle.kts   vernont-application/
COPY vernont-workflow/build.gradle.kts      vernont-workflow/
COPY vernont-events/build.gradle.kts        vernont-events/
COPY vernont-infrastructure/build.gradle.kts vernont-infrastructure/
COPY vernont-api/build.gradle.kts           vernont-api/

# Module sources
COPY vernont-domain/src         vernont-domain/src
COPY vernont-application/src    vernont-application/src
COPY vernont-workflow/src       vernont-workflow/src
COPY vernont-events/src         vernont-events/src
COPY vernont-infrastructure/src vernont-infrastructure/src
COPY vernont-api/src            vernont-api/src

# Build the vernont-api boot jar (with cached Gradle home to speed up rebuilds)
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew :vernont-api:bootJar \
    --no-daemon \
    --console=plain \
    --info \
    --stacktrace


# -------- RUNTIME STAGE --------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# curl for healthcheck + non-root user
RUN apk add --no-cache curl libgcc libstdc++ \
 && addgroup -g 1000 vernont \
 && adduser -D -u 1000 -G vernont vernont

# Copy built jar
COPY --from=build /home/gradle/project/vernont-api/build/libs/*.jar app.jar

RUN mkdir -p /app/logs && chown -R vernont:vernont /app
USER vernont

# Spring Boot default port
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
