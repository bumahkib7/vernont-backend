#!/bin/bash

# NexusCommerce Docker Update Script
# Builds new app image, pulls latest service images and recreates containers

set -e

echo "🔄 NexusCommerce Docker Update"
echo "================================"

# Determine docker compose command
if command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE_CMD="docker-compose"
else
    DOCKER_COMPOSE_CMD="docker compose"
fi

# Build the NexusCommerce application image
echo ""
echo "🔨 Building NexusCommerce application image..."
./gradlew :nexus-api:jibDockerBuild --no-daemon

# Pull latest images for services
echo ""
echo "📦 Pulling latest Docker images for services..."
$DOCKER_COMPOSE_CMD pull postgres redis minio elasticsearch ollama prometheus grafana || {
    echo "⚠️  Warning: Some images failed to pull, continuing..."
}

# Recreate containers with new images
echo ""
echo "🔧 Recreating containers..."
$DOCKER_COMPOSE_CMD up -d --force-recreate --remove-orphans

# Clean up old images
echo ""
echo "🧹 Cleaning up old images..."
docker image prune -f

echo ""
echo "✅ Update complete!"
echo ""
echo "📊 Service Status:"
$DOCKER_COMPOSE_CMD ps
