#!/bin/bash

# NexusCommerce Docker Startup Script
# This script automatically builds the app image and pulls other images before starting services

set -e

echo "🚀 NexusCommerce Docker Startup"
echo "================================"

# Check if docker-compose is installed
if ! command -v docker-compose &> /dev/null && ! command -v docker &> /dev/null; then
    echo "❌ Error: docker-compose or docker is not installed"
    exit 1
fi

# Determine docker compose command
if command -v docker-compose &> /dev/null; then
    DOCKER_COMPOSE_CMD="docker-compose"
else
    DOCKER_COMPOSE_CMD="docker compose"
fi

# Build the NexusCommerce application image first
echo ""
echo "🔨 Building NexusCommerce application image..."
./gradlew :nexus-api:jibDockerBuild --no-daemon

# Pull latest images for services (excluding the app)
echo ""
echo "📦 Pulling latest Docker images for services..."
$DOCKER_COMPOSE_CMD pull postgres redis minio elasticsearch ollama prometheus grafana || {
    echo "⚠️  Warning: Some images failed to pull, continuing with cached versions..."
}

# Start services
echo ""
echo "🔧 Starting services..."
$DOCKER_COMPOSE_CMD up -d

# Wait for services to be healthy
echo ""
echo "⏳ Waiting for services to start..."
sleep 10

# Check service status
echo ""
echo "📊 Service Status:"
$DOCKER_COMPOSE_CMD ps

echo ""
echo "✅ NexusCommerce started successfully!"
echo ""
echo "Access points:"
echo "  - API: http://localhost:8080"
echo "  - API Health: http://localhost:8080/actuator/health"
echo "  - Prometheus: http://localhost:9090"
echo "  - Grafana: http://localhost:3001 (admin/admin)"
echo "  - MinIO Console: http://localhost:9001 (minioadmin/minioadmin)"
echo "  - Elasticsearch: http://localhost:9200"
echo ""
echo "To view logs: $DOCKER_COMPOSE_CMD logs -f [service-name]"
echo "To stop: $DOCKER_COMPOSE_CMD down"
