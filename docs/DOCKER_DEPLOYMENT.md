# Docker Deployment Guide

This guide explains how to build and deploy NexusCommerce using Docker and Jib.

## Understanding the Image

The `vernont-backend:latest` image is **built locally** using Gradle Jib, not pulled from Docker Hub.

### Jib Configuration

Located in `vernont-api/build.gradle.kts`:
- **Base Image**: `eclipse-temurin:21-jre-jammy` (ARM64)
- **Target Image**: `vernont-backend:latest` (configurable via `JIB_TO_IMAGE`)
- **Platform**: ARM64 (for Apple Silicon / ARM servers)

## Quick Start

### 1. Build the Application Image

```bash
./docker-build.sh
```

This runs: `./gradlew :vernont-api:jibDockerBuild --no-daemon`

### 2. Start All Services

```bash
./docker-start.sh
```

This will:
1. Build the application image with Jib
2. Pull service images (Postgres, Redis, etc.)
3. Start all containers

### 3. Update Services

```bash
./docker-update.sh
```

This will:
1. Rebuild the application image
2. Pull latest service images
3. Recreate all containers
4. Clean up old images

## Manual Build Commands

### Build to Local Docker Daemon (Recommended)
```bash
./gradlew :vernont-api:jibDockerBuild
```

### Build and Push to Registry
```bash
export JIB_TO_IMAGE=your-registry.com/vernont-backend:latest
./gradlew :vernont-api:jib
```

### Build without Docker Daemon (creates tar)
```bash
./gradlew :vernont-api:jibBuildTar
```

## Troubleshooting

### Error: "pull access denied for vernont-backend"

**Solution**: This means the image wasn't built locally. Run:
```bash
./docker-build.sh
# OR
./gradlew :vernont-api:jibDockerBuild
```

### Verify the Image Exists
```bash
docker images | grep vernont-backend
```

You should see:
```
vernont-backend   latest   abc123def456   2 minutes ago   500MB
```

### Build from Scratch
```bash
# Stop all containers
docker-compose down

# Remove old image
docker rmi vernont-backend:latest

# Rebuild
./docker-build.sh

# Start
./docker-start.sh
```

## Environment Variables

### Customize Image Name
```bash
export JIB_TO_IMAGE=my-custom-name:v1.0
./gradlew :vernont-api:jibDockerBuild
```

### Update docker-compose.yml
```yaml
vernont:
  image: ${NEXUS_IMAGE:-my-custom-name:v1.0}
```

## Deployment to Production

### 1. Build for Production Registry
```bash
export JIB_TO_IMAGE=registry.example.com/vernont-backend:1.0.0
./gradlew :vernont-api:jib
```

### 2. On Production Server
```bash
docker-compose pull
docker-compose up -d
```

## Scripts Reference

| Script | Purpose |
|--------|---------|
| `./docker-build.sh` | Build application image only |
| `./docker-start.sh` | Build + Pull + Start all services |
| `./docker-update.sh` | Rebuild + Update + Restart all services |

## Architecture Notes

The Jib build is configured for **ARM64** architecture. If deploying to x86_64 servers, update `vernont-api/build.gradle.kts`:

```kotlin
jib {
    from {
        platforms {
            platform {
                os = "linux"
                architecture = "amd64"  // Change from arm64
            }
        }
    }
}
```
