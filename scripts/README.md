# Deploy to Elastic Beanstalk Scripts

## Quick Start

### Step 1: Generate Secrets

```bash
./scripts/generate-secrets.sh
```

This will generate:
- JWT_SECRET (256-bit secure token)
- ADMIN_PASSWORD (32-char random password)

**Copy these somewhere safe!**

### Step 2: Edit the Update Script

```bash
nano scripts/update-beanstalk-env.sh
```

Or use your preferred editor (vim, VSCode, etc.)

**Replace these variables** (lines 14-38):

**Required (app won't start without these):**
- `DATABASE_PASSWORD` - Your RDS PostgreSQL password
- `JWT_SECRET` - Paste from Step 1
- `ADMIN_PASSWORD` - Paste from Step 1

**Required for features:**
- `AWS_S3_ACCESS_KEY` - Your S3 access key (for product images)
- `AWS_S3_SECRET_KEY` - Your S3 secret key

**Optional (can leave empty or add later):**
- `GOOGLE_CLIENT_ID` - For Google OAuth login
- `GOOGLE_CLIENT_SECRET` - For Google OAuth login
- `MAILERSEND_TOKEN` - For sending emails
- `RAKUTEN_CLIENT_ID` - Affiliate network
- `RAKUTEN_CLIENT_SECRET` - Affiliate network
- `FLEXOFFERS_API_KEY` - Affiliate network
- `CJ_API_KEY` - Commission Junction affiliate network

### Step 3: Run the Update Script

```bash
./scripts/update-beanstalk-env.sh
```

This will:
1. Validate required variables are set
2. Update all environment variables in Elastic Beanstalk
3. Take 2-3 minutes to apply

### Step 4: Verify

Check the Beanstalk environment:

```bash
aws elasticbeanstalk describe-environments \
  --environment-names neoxus-production \
  --region eu-north-1 \
  --query 'Environments[0].Status'
```

Or visit AWS Console:
https://eu-north-1.console.aws.amazon.com/elasticbeanstalk/home?region=eu-north-1#/environment/dashboard?environmentId=neoxus-production

## Troubleshooting

**"Unable to locate credentials"**
```bash
aws configure
# Enter your AWS Access Key ID and Secret Access Key
```

**Environment update failed**
Check the Beanstalk events in AWS Console for error details.

**App not starting**
- Verify DATABASE_PASSWORD is correct
- Check RDS security group allows Beanstalk security group
- Check ElastiCache security group allows Beanstalk security group
- Review logs in AWS Console → Beanstalk → Logs

## What Gets Configured

✅ **Database**: RDS PostgreSQL connection
✅ **Redis**: ElastiCache Serverless
✅ **Elasticsearch**: https://es.neoxus.co.uk
✅ **S3**: For product images and file uploads
✅ **JWT**: Secure token generation
✅ **CORS**: Allowed origins for API
✅ **Email**: MailerSend integration
✅ **Affiliate Networks**: Rakuten, FlexOffers, CJ
✅ **Spring Profiles**: Production mode
✅ **JPA**: Validate-only (no auto schema changes)

## Security Notes

🔒 **Never commit these scripts with real secrets to Git!**

The script is in `.gitignore` - keep it that way.

Store production secrets in:
- AWS Secrets Manager
- 1Password / LastPass
- Encrypted vault

## Files Created

- `update-beanstalk-env.sh` - Main deployment script
- `generate-secrets.sh` - Secret generator helper
- `README.md` - This file
- `../BEANSTALK_ENV_VARS.txt` - Reference document
- `../.ebextensions/environment.config` - Alternative config method
