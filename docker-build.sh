#!/bin/bash

# NexusCommerce Docker Build Script
# Builds the application Docker image only

set -e

echo "🔨 Building NexusCommerce Docker Image"
echo "========================================"

# Build with Gradle Jib
echo ""
echo "Building application JAR and Docker image..."
./gradlew :nexus-api:jibDockerBuild --no-daemon

echo ""
echo "✅ Build complete!"
echo ""
echo "Image: vernont-backend:latest"
echo ""
echo "To start services: ./docker-start.sh"
echo "To see images: docker images | grep vernont-backend"
