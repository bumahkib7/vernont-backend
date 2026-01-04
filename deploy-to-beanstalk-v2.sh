#!/bin/bash

#########################################
# AWS Elastic Beanstalk Deployment Script
# Builds with Jib (Docker daemon) and deploys to AWS Beanstalk
#########################################

set -e

# Configuration
ENVIRONMENT_NAME="${BEANSTALK_ENV:-neoxus-production-v2}"
REGION="${AWS_REGION:-eu-north-1}"
ECR_REPOSITORY="${ECR_REPO:-459722925345.dkr.ecr.eu-north-1.amazonaws.com/vernont-backend}"
IMAGE_TAG="${IMAGE_TAG:-$(date +%Y%m%d-%H%M%S)}"
VERSION_LABEL="nexus-${IMAGE_TAG}"
LOCAL_IMAGE="vernont-backend:${IMAGE_TAG}"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to wait for environment to be ready
wait_for_environment_ready() {
    local env_name=$1
    local region=$2
    local max_wait=600  # 10 minutes max
    local elapsed=0

    echo -e "${YELLOW}Checking environment status...${NC}"

    while [ $elapsed -lt $max_wait ]; do
        status=$(aws elasticbeanstalk describe-environments \
            --environment-names "$env_name" \
            --region "$region" \
            --query 'Environments[0].Status' \
            --output text 2>/dev/null)

        if [ "$status" = "Ready" ]; then
            echo -e "${GREEN}✓${NC} Environment is ready"
            return 0
        elif [ "$status" = "Updating" ] || [ "$status" = "Launching" ]; then
            echo -e "${YELLOW}⏳${NC} Environment is ${status}... waiting (${elapsed}s elapsed)"
            sleep 10
            elapsed=$((elapsed + 10))
        else
            echo -e "${RED}✗${NC} Environment is in unexpected state: ${status}"
            return 1
        fi
    done

    echo -e "${RED}✗${NC} Timeout waiting for environment to be ready"
    return 1
}

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   Nexus Commerce Deployment to AWS    ${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}Environment:${NC} $ENVIRONMENT_NAME"
echo -e "${YELLOW}Region:${NC} $REGION"
echo -e "${YELLOW}ECR Repository:${NC} $ECR_REPOSITORY"
echo -e "${YELLOW}Image Tag:${NC} $IMAGE_TAG"
echo ""

# Step 1: Build with Jib to local Docker daemon
echo -e "${BLUE}[1/6]${NC} Building Docker image with Jib (local Docker daemon)..."
./gradlew :nexus-api:jibDockerBuild \
    -Djib.to.image="${LOCAL_IMAGE}" \
    -Djib.console=plain
echo -e "${GREEN}✓${NC} Image built locally: ${LOCAL_IMAGE}"
echo ""

# Step 2: Tag for ECR
echo -e "${BLUE}[2/6]${NC} Tagging image for ECR..."
docker tag "${LOCAL_IMAGE}" "${ECR_REPOSITORY}:${IMAGE_TAG}"
docker tag "${LOCAL_IMAGE}" "${ECR_REPOSITORY}:latest"
echo -e "${GREEN}✓${NC} Image tagged"
echo ""

# Step 3: AWS ECR Login
echo -e "${BLUE}[3/6]${NC} Logging into AWS ECR..."
aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin "$(echo $ECR_REPOSITORY | cut -d'/' -f1)"
echo -e "${GREEN}✓${NC} ECR login successful"
echo ""

# Step 4: Push to ECR
echo -e "${BLUE}[4/6]${NC} Pushing image to ECR..."
docker push "${ECR_REPOSITORY}:${IMAGE_TAG}"
docker push "${ECR_REPOSITORY}:latest"
echo -e "${GREEN}✓${NC} Image pushed to ECR"
echo ""

# Step 5: Create Dockerrun.aws.json
echo -e "${BLUE}[5/6]${NC} Creating Dockerrun.aws.json..."
cat > Dockerrun.aws.json <<EOF
{
  "AWSEBDockerrunVersion": "1",
  "Image": {
    "Name": "${ECR_REPOSITORY}:${IMAGE_TAG}",
    "Update": "true"
  },
  "Ports": [
    {
      "ContainerPort": 8080,
      "HostPort": 8080
    }
  ],
  "Logging": "/var/log/nginx"
}
EOF
echo -e "${GREEN}✓${NC} Dockerrun.aws.json created"
echo ""

# Create application version
zip -q deployment.zip Dockerrun.aws.json

# Upload to S3
S3_BUCKET="elasticbeanstalk-${REGION}-$(aws sts get-caller-identity --query Account --output text)"
S3_KEY="vernont-backend/${VERSION_LABEL}.zip"

aws s3 cp deployment.zip "s3://${S3_BUCKET}/${S3_KEY}" --region $REGION

# Create application version
aws elasticbeanstalk create-application-version \
    --application-name "neoxus-backend" \
    --version-label "${VERSION_LABEL}" \
    --source-bundle "S3Bucket=${S3_BUCKET},S3Key=${S3_KEY}" \
    --region $REGION \
    --no-auto-create-application

echo -e "${GREEN}✓${NC} Application version created: ${VERSION_LABEL}"
echo ""

# Step 6: Wait for environment to be ready, then deploy
echo -e "${BLUE}[6/6]${NC} Deploying to Elastic Beanstalk..."
wait_for_environment_ready "${ENVIRONMENT_NAME}" "${REGION}"
if [ $? -ne 0 ]; then
    echo -e "${RED}✗${NC} Cannot deploy - environment not ready"
    exit 1
fi

aws elasticbeanstalk update-environment \
    --environment-name "${ENVIRONMENT_NAME}" \
    --version-label "${VERSION_LABEL}" \
    --region $REGION

echo -e "${GREEN}✓${NC} Deployment initiated!"
echo ""

# Cleanup
rm -f Dockerrun.aws.json deployment.zip
docker rmi "${LOCAL_IMAGE}" 2>/dev/null || true

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}   Deployment Initiated Successfully!   ${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${YELLOW}Monitor deployment status:${NC}"
echo "  aws elasticbeanstalk describe-environments --environment-names ${ENVIRONMENT_NAME} --region ${REGION} --query 'Environments[0].Status'"
echo ""
echo -e "${YELLOW}View logs:${NC}"
echo "  aws logs tail /aws/elasticbeanstalk/${ENVIRONMENT_NAME}/var/log/eb-docker/containers/eb-current-app/stdouterr.log --follow --region ${REGION} --format short"
echo ""
echo -e "${YELLOW}AWS Console:${NC}"
echo "  https://${REGION}.console.aws.amazon.com/elasticbeanstalk/home?region=${REGION}#/environment/dashboard?environmentName=${ENVIRONMENT_NAME}"
echo ""
echo -e "${BLUE}Deployment will take 3-5 minutes to complete...${NC}"
