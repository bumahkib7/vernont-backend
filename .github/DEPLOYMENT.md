# GitHub Actions Deployment to AWS Elastic Beanstalk

This repository is configured to automatically deploy to AWS Elastic Beanstalk on every push to the `main` branch.

## Setup Instructions

### 1. Configure GitHub Secrets

You need to add AWS credentials to your GitHub repository:

1. Go to your GitHub repository
2. Navigate to: **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Add the following secrets:

   - **AWS_ACCESS_KEY_ID**: Your AWS access key
   - **AWS_SECRET_ACCESS_KEY**: Your AWS secret access key

### 2. Create IAM User (if not already done)

Create an IAM user in AWS with the following permissions:

1. Go to AWS IAM Console → Users → Create user
2. User name: `github-actions-deployer` (or any name you prefer)
3. Attach policies (choose one option):

   **Option A - AWS Managed Policies (Easiest):**
   - `AdministratorAccess-AWSElasticBeanstalk`
   - `AmazonS3FullAccess`

   **Option B - Custom Policy (More Secure):**
   Create a custom policy with these permissions:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       {
         "Effect": "Allow",
         "Action": [
           "elasticbeanstalk:*",
           "s3:*",
           "ec2:*",
           "cloudformation:*",
           "autoscaling:*",
           "elasticloadbalancing:*"
         ],
         "Resource": "*"
       }
     ]
   }
   ```

4. Create access key:
   - Security credentials → Access keys → Create access key
   - Use case: Application running outside AWS
   - Save the Access Key ID and Secret Access Key

### 3. Deployment Configuration

The workflow is configured for:

- **Application**: neoxus-backend
- **Environment**: neoxus-production
- **Region**: eu-north-1
- **Platform**: Corretto 25 on Amazon Linux 2023

### 4. How It Works

**Automatic Deployment:**
- Every push to `main` branch triggers deployment automatically
- The workflow builds the JAR file using `./gradlew :vernont-api:bootJar`
- Deploys to Elastic Beanstalk environment

**Manual Deployment:**
- Go to **Actions** tab in GitHub
- Select "Deploy to Elastic Beanstalk" workflow
- Click **Run workflow**
- Select branch and click **Run workflow**

### 5. Monitoring Deployments

View deployment status:
- GitHub: **Actions** tab shows build and deployment progress
- AWS Console: Elastic Beanstalk → neoxus-production → Events

### 6. Environment Variables

If your application requires environment variables (database credentials, API keys, etc.):

1. AWS Console → Elastic Beanstalk → neoxus-production
2. Configuration → Updates, monitoring, and logging → Edit
3. Scroll to **Environment properties**
4. Add your environment variables

Common variables you might need:
```
SPRING_PROFILES_ACTIVE=production
SPRING_DATASOURCE_URL=jdbc:postgresql://...
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
JWT_SECRET=...
```

### 7. Troubleshooting

**Deployment fails:**
- Check GitHub Actions logs for build errors
- Check Elastic Beanstalk logs in AWS Console
- Verify environment health in AWS Console

**Application not starting:**
- Check Elastic Beanstalk logs: `/var/log/web.stdout.log`
- Verify environment variables are set correctly
- Ensure database is accessible from Elastic Beanstalk

**Build fails:**
- Verify all dependencies are available
- Check if tests are passing (currently skipped with `-x test`)

### 8. Deployment Workflow

```
Push to main
    ↓
GitHub Actions triggered
    ↓
Checkout code
    ↓
Set up JDK 25 (Corretto)
    ↓
Build JAR (vernont-api-1.0.0.jar)
    ↓
Configure AWS credentials
    ↓
Deploy to Elastic Beanstalk
    ↓
Wait for deployment completion
    ↓
✅ Deployment successful
```

### 9. Cost Optimization

GitHub Actions free tier:
- 2,000 minutes/month for private repos
- Unlimited for public repos

This deployment typically takes ~5-8 minutes per run.

### 10. Security Best Practices

✅ **DO:**
- Use IAM user with minimal required permissions
- Rotate access keys regularly
- Use different credentials for different environments
- Enable MFA for AWS account

❌ **DON'T:**
- Never commit AWS credentials to code
- Don't use root AWS account credentials
- Don't share credentials between projects

---

## Quick Start

Once secrets are configured:

```bash
git add .
git commit -m "Deploy to production"
git push origin main
```

Watch deployment progress in GitHub Actions tab!
