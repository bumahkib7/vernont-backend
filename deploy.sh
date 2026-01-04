#!/bin/bash
set -e

# 1. Pull latest changes
echo "Pulling latest changes..."
git pull



# 3. Build the Docker image using Jib
echo "Building the Docker image with Jib..."
./gradlew jibDockerBuild

# 4. Restart only the backend service
echo "Restarting backend container..."
docker compose up -d --no-deps --force-recreate vernont

# 5. Clean up dangling/unused images
echo "Pruning unused Docker images..."
docker image prune -f

# 5. Tail the logs
echo "Tailing logs..."
docker compose logs -f vernont
