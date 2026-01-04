#!/bin/bash

#########################################
# AWS Elastic Beanstalk Deployment Script
# Uses Jib (no Docker required)
#########################################

set -e

# Configuration
ENVIRONMENT_NAME="${BEANSTALK_ENV:-neoxus-production-v2}"
REGION="${AWS_REGION:-eu-north-1}"
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"
ECR_REPOSITORY="vernont-backend"
IMAGE_TAG="${IMAGE_TAG:-$(date +%Y%m%d-%H%M%S)}"
VERSION_LABEL="nexus-${IMAGE_TAG}"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   Nexus Commerce Deployment to AWS    ${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}Environment:${NC} $ENVIRONMENT_NAME"
echo -e "${YELLOW}Region:${NC} $REGION"
echo -e "${YELLOW}ECR Registry:${NC} $ECR_REGISTRY"
echo -e "${YELLOW}ECR Repository:${NC} $ECR_REPOSITORY"
echo -e "${YELLOW}Image Tag:${NC} $IMAGE_TAG"
echo ""

# Step 1: Get ECR credentials
echo -e "${BLUE}[1/4]${NC} Getting ECR credentials..."
ECR_PASSWORD=$(aws ecr get-login-password --region "$REGION")
echo -e "${GREEN}✓${NC} ECR credentials obtained"
echo ""

# Step 2: Build and push with Jib
echo -e "${BLUE}[2/4]${NC} Building and pushing image with Jib..."
./gradlew :nexus-api:jib \
    -Djib.to.image="${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}" \
    -Djib.to.tags=latest \
    -Djib.to.auth.username=AWS \
    -Djib.to.auth.password="${ECR_PASSWORD}" \
    -Djib.console=plain \
    --no-daemon

echo -e "${GREEN}✓${NC} Image built and pushed: ${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}"
echo ""

# Step 3: Create and upload application version
echo -e "${BLUE}[3/4]${NC} Creating application version..."

# Create Dockerrun.aws.json
cat > Dockerrun.aws.json <<EOF
{
  "AWSEBDockerrunVersion": "1",
  "Image": {
    "Name": "${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}",
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

# Create deployment package
zip -q deployment.zip Dockerrun.aws.json

# Upload to S3
S3_BUCKET="elasticbeanstalk-${REGION}-${AWS_ACCOUNT_ID}"
S3_KEY="vernont-backend/${VERSION_LABEL}.zip"

echo "  Uploading to S3..."
aws s3 cp deployment.zip "s3://${S3_BUCKET}/${S3_KEY}" --region $REGION

# Create application version
echo "  Creating Beanstalk application version..."
aws elasticbeanstalk create-application-version \
    --application-name "neoxus-backend" \
    --version-label "${VERSION_LABEL}" \
    --source-bundle "S3Bucket=${S3_BUCKET},S3Key=${S3_KEY}" \
    --region "$REGION" \
    --no-auto-create-application

echo -e "${GREEN}✓${NC} Application version created: ${VERSION_LABEL}"
echo ""

# Step 4: Deploy to Beanstalk
echo -e "${BLUE}[4/4]${NC} Deploying to Elastic Beanstalk..."
aws elasticbeanstalk update-environment \
    --environment-name "${ENVIRONMENT_NAME}" \
    --version-label "${VERSION_LABEL}" \
    --region "$REGION"

echo -e "${GREEN}✓${NC} Deployment initiated!"
echo ""

# Cleanup
rm -f Dockerrun.aws.json deployment.zip

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}   Deployment Initiated Successfully!   ${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${YELLOW}Monitor deployment:${NC}"
echo "  aws elasticbeanstalk describe-environments \\"
echo "    --environment-names ${ENVIRONMENT_NAME} \\"
echo "    --region ${REGION} \\"
echo "    --query 'Environments[0].Status'"
echo ""
echo -e "${YELLOW}View logs:${NC}"
echo "  aws logs tail /aws/elasticbeanstalk/${ENVIRONMENT_NAME}/var/log/eb-docker/containers/eb-current-app/stdouterr.log \\"
echo "    --follow --region ${REGION} --format short"
echo ""
echo -e "${YELLOW}AWS Console:${NC}"
echo "  https://${REGION}.console.aws.amazon.com/elasticbeanstalk/home?region=${REGION}#/environment/dashboard?environmentName=${ENVIRONMENT_NAME}"
echo ""
echo -e "${BLUE}Deployment will take 3-5 minutes...${NC}"
