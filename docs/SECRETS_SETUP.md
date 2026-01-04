# Secrets Management Guide

## Overview

Vernont uses environment variables for all sensitive configuration. **Never** commit secrets to version control.

## Quick Start

1. Copy the template:
   ```bash
   cp .env.example .env
   ```

2. Fill in required values in `.env`

3. Verify `.env` is in `.gitignore`

## Required Secrets

### Critical (Must be set for production)

#### JWT Authentication
```bash
# Generate a secure random key (minimum 256 bits for HS512)
JWT_SECRET=$(openssl rand -base64 64)
NEXUS_JWT_SECRET=$JWT_SECRET
```

#### Database
```bash
DATABASE_URL=jdbc:postgresql://your-db-host:5432/vernont
DATABASE_USER=your_db_user
DATABASE_PASSWORD=<secure_random_password>
```

#### Redis
```bash
REDIS_HOST=your-redis-host
REDIS_PORT=6379
REDIS_PASSWORD=<secure_random_password>
```

### OAuth & Social Login

#### Google OAuth
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create OAuth 2.0 credentials
3. Add authorized redirect URIs
4. Set environment variables:
```bash
GOOGLE_OAUTH_CLIENT_ID=<from_google_console>
GOOGLE_OAUTH_CLIENT_SECRET=<from_google_console>
```

### Affiliate Networks

#### Commission Junction (CJ)
```bash
CJ_API_KEY=<from_cj_account>
CJ_WEBSITE_ID=<your_website_id>
```

#### FlexOffers
```bash
FLEXOFFERS_API_KEY=<from_flexoffers_account>
```

#### Rakuten (Optional)
```bash
RAKUTEN_CLIENT_ID=<from_rakuten>
RAKUTEN_CLIENT_SECRET=<from_rakuten>
```

### Pinterest (Optional)

```bash
PINTEREST_APP_SECRET=<from_pinterest_developer_portal>
PINTEREST_ACCESS_TOKEN=<from_pinterest_developer_portal>
PINTEREST_DEFAULT_BOARD_ID=<your_board_id>
PINTEREST_ENABLED=true  # Set to true to enable
```

### Email Services

Choose one or configure both:

```bash
# SendGrid
SENDGRID_API_KEY=<from_sendgrid>

# MailerSend
NEXUS_MAILERSEND_TOKEN=<from_mailersend>
```

### Cloud Storage

#### AWS S3 / MinIO
```bash
AWS_ACCESS_KEY=<your_access_key>
AWS_SECRET_KEY=<your_secret_key>
```

## Production Deployment Options

### Option 1: Kubernetes Secrets (Recommended)

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: vernont-secrets
type: Opaque
stringData:
  JWT_SECRET: <base64_encoded>
  DATABASE_PASSWORD: <base64_encoded>
  # ... other secrets
```

Apply secrets:
```bash
kubectl apply -f secrets.yaml
```

Reference in deployment:
```yaml
envFrom:
  - secretRef:
      name: vernont-secrets
```

### Option 2: AWS Secrets Manager

```bash
# Store secret
aws secretsmanager create-secret \
  --name nexus/jwt-secret \
  --secret-string "your-secret-value"

# Retrieve in application
aws secretsmanager get-secret-value \
  --secret-id nexus/jwt-secret
```

### Option 3: HashiCorp Vault

```bash
# Write secret
vault kv put secret/nexus/jwt JWT_SECRET="your-secret"

# Read secret
vault kv get secret/nexus/jwt
```

### Option 4: Azure Key Vault

```bash
# Create secret
az keyvault secret set \
  --vault-name vernont-vault \
  --name JWT-SECRET \
  --value "your-secret"
```

### Option 5: Docker Secrets (Docker Swarm)

```bash
# Create secret
echo "your-secret" | docker secret create jwt_secret -

# Use in docker-compose.yml
secrets:
  jwt_secret:
    external: true
```

### Option 6: Environment Variables (Simple deployments)

Set in your deployment script:
```bash
export JWT_SECRET="..."
export DATABASE_PASSWORD="..."
# ... etc

java -jar vernont-backend.jar
```

## Security Best Practices

### Secret Generation

```bash
# Strong random secrets (64 bytes = 512 bits)
openssl rand -base64 64

# UUID-based secrets
uuidgen

# Alphanumeric secrets
tr -dc A-Za-z0-9 </dev/urandom | head -c 32
```

### Secret Rotation Schedule

| Secret Type | Rotation Frequency | Priority |
|------------|-------------------|----------|
| JWT_SECRET | Every 90 days | High |
| Database passwords | Every 90 days | High |
| API keys (affiliate) | When provider recommends | Medium |
| OAuth secrets | Every 180 days | Medium |
| AWS credentials | Every 90 days | High |

### Rotation Process

1. **Preparation**
   - Generate new secret
   - Update in secret management system
   - Plan maintenance window

2. **Update Application**
   - Deploy new secret
   - Monitor for errors
   - Verify functionality

3. **Cleanup**
   - Revoke old secret after grace period
   - Update documentation
   - Audit logs

### Never Do This ❌

```yaml
# DON'T: Hardcode secrets in config files
jwt:
  secret: "MySecretKey123"  # NEVER!

# DON'T: Commit .env files
git add .env  # NEVER!

# DON'T: Log secrets
logger.info("Password: " + password)  # NEVER!

# DON'T: Share secrets in chat/email
```

### Always Do This ✅

```yaml
# DO: Use environment variables
jwt:
  secret: ${JWT_SECRET:}

# DO: Use secret management
kubectl create secret generic vernont-secrets --from-env-file=.env

# DO: Redact secrets in logs
logger.info("Login attempt for user: " + email)  # Don't log password

# DO: Use secure channels
# Use encrypted secret management tools
```

## Local Development

### Setup
```bash
# 1. Copy template
cp .env.example .env

# 2. Generate development secrets (DO NOT use in production)
echo "JWT_SECRET=$(openssl rand -base64 64)" >> .env
echo "DATABASE_PASSWORD=dev_password_$(openssl rand -hex 8)" >> .env

# 3. Start application
./gradlew bootRun
```

### Development Values
For local development, you can use simplified values:

```bash
# Local dev database
DATABASE_URL=jdbc:postgresql://localhost:5432/vernont_dev
DATABASE_USER=dev_user
DATABASE_PASSWORD=dev_password

# Local Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=  # Empty for local dev

# JWT (generate new for each dev environment)
JWT_SECRET=$(openssl rand -base64 64)
```

## Troubleshooting

### Application won't start

**Symptom**: "Could not resolve placeholder 'JWT_SECRET'"

**Solution**: Ensure `.env` file exists and contains all required variables

```bash
# Check if .env exists
ls -la .env

# Verify it's being loaded
export $(cat .env | xargs)
./gradlew bootRun
```

### OAuth not working

**Symptom**: "Invalid client credentials"

**Solution**:
1. Verify credentials in Google/provider console
2. Check authorized redirect URIs match your domain
3. Ensure secrets are properly set

```bash
# Test if env vars are set
echo $GOOGLE_OAUTH_CLIENT_ID
echo $GOOGLE_OAUTH_CLIENT_SECRET
```

### Affiliate APIs failing

**Symptom**: 401 Unauthorized from CJ/FlexOffers

**Solution**:
1. Verify API keys are current
2. Check account status with provider
3. Verify IP whitelist if applicable

## Emergency Response

### Compromised Secret

If a secret is compromised:

1. **Immediate Actions**
   ```bash
   # 1. Generate new secret
   NEW_SECRET=$(openssl rand -base64 64)

   # 2. Update in all environments
   # 3. Revoke old secret
   # 4. Audit access logs
   ```

2. **Affected Systems**
   - Identify all systems using the secret
   - Update simultaneously if possible
   - Monitor for unauthorized access

3. **Post-Incident**
   - Document the incident
   - Review access controls
   - Update rotation schedule if needed

## Compliance & Auditing

### Audit Checklist

- [ ] No secrets in version control
- [ ] `.env` in `.gitignore`
- [ ] Secrets rotated per schedule
- [ ] Access logged and monitored
- [ ] Secrets encrypted at rest
- [ ] Secure transmission (TLS)
- [ ] Principle of least privilege applied

### Logging

Vernont logs secret usage (without exposing values):

```
2025-12-27 10:00:00 INFO - JWT token generated for user: user@example.com
2025-12-27 10:00:01 INFO - Database connection established
2025-12-27 10:00:02 INFO - Redis connection established
```

**Never logged**: Actual secret values, passwords, tokens

## Support

For security concerns: security@nexus.com
For setup help: See main README.md
