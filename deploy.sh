#!/bin/bash
# Vernont Backend Deployment Script
# Usage: ./deploy.sh [staging|production|all]

set -e

# Configuration
AWS_REGION="eu-north-1"
ECR_REPO="459722925345.dkr.ecr.eu-north-1.amazonaws.com/vernont-backend"
S3_BUCKET="vernont-artifacts-eu-north-1"
APP_NAME="vernont-backend"
STAGING_ENV="vernont-staging-v2"
PRODUCTION_ENV="vernont-production-v2"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Parse arguments
TARGET=${1:-all}

if [[ "$TARGET" != "staging" && "$TARGET" != "production" && "$TARGET" != "all" ]]; then
    echo "Usage: $0 [staging|production|all]"
    echo "  staging    - Deploy to staging only"
    echo "  production - Deploy to production only"
    echo "  all        - Deploy to both (default)"
    exit 1
fi

log_info "Starting deployment to: $TARGET"

# Step 1: Login to ECR
log_info "Logging in to ECR..."
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ECR_REPO

# Step 2: Build and push Docker image with JIB
log_info "Building and pushing Docker image..."
JIB_TO_IMAGE="${ECR_REPO}:latest" ./gradlew :vernont-api:jib --no-daemon

# Step 3: Create version label
VERSION_LABEL="vernont-$(date +%Y%m%d-%H%M%S)"
log_info "Creating version: $VERSION_LABEL"

# Step 4: Create deployment bundle
DEPLOY_ZIP="/tmp/${VERSION_LABEL}.zip"
zip -r "$DEPLOY_ZIP" Dockerrun.aws.json

# Step 5: Upload to S3
log_info "Uploading to S3..."
aws s3 cp "$DEPLOY_ZIP" "s3://${S3_BUCKET}/deployments/${VERSION_LABEL}.zip" --region $AWS_REGION

# Step 6: Create application version
log_info "Creating application version..."
aws elasticbeanstalk create-application-version \
    --application-name $APP_NAME \
    --version-label $VERSION_LABEL \
    --source-bundle S3Bucket=$S3_BUCKET,S3Key=deployments/${VERSION_LABEL}.zip \
    --region $AWS_REGION \
    --query 'ApplicationVersion.VersionLabel' \
    --output text

# Step 7: Deploy to environments
if [[ "$TARGET" == "staging" || "$TARGET" == "all" ]]; then
    log_info "Deploying to staging..."
    aws elasticbeanstalk update-environment \
        --environment-name $STAGING_ENV \
        --version-label $VERSION_LABEL \
        --region $AWS_REGION \
        --query 'Status' \
        --output text
fi

if [[ "$TARGET" == "production" || "$TARGET" == "all" ]]; then
    log_info "Deploying to production..."
    aws elasticbeanstalk update-environment \
        --environment-name $PRODUCTION_ENV \
        --version-label $VERSION_LABEL \
        --region $AWS_REGION \
        --query 'Status' \
        --output text
fi

# Cleanup
rm -f "$DEPLOY_ZIP"

log_info "Deployment initiated: $VERSION_LABEL"
echo ""
log_info "Endpoints:"
log_info "  Staging:    http://vernont-staging-v2.eba-6yivaxan.eu-north-1.elasticbeanstalk.com"
log_info "  Production: http://vernont-production-v2.eba-6yivaxan.eu-north-1.elasticbeanstalk.com"
