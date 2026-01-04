# 👑 Admin User Setup Guide

## 🎯 Creating Your First Admin User

Since admin users can't use the public registration endpoint, we provide **3 secure methods** to create your first admin user:

### Method 1: Bootstrap Endpoint (Recommended for Development)

**Step 1: Set Bootstrap Secret**
```bash
# Set a secure secret key (required for security)
export ADMIN_BOOTSTRAP_SECRET="your-super-secure-secret-key-here"
```

**Step 2: Use Bootstrap Endpoint**
```bash
curl -X POST http://localhost:8080/admin/bootstrap/create-admin \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@yourcompany.com",
    "password": "SuperSecureAdmin123!",
    "secretKey": "your-super-secure-secret-key-here",
    "firstName": "Admin",
    "lastName": "User"
  }'
```

**Step 3: Bootstrap Auto-Disables**
- ✅ After first admin creation, the bootstrap endpoint is automatically disabled
- ✅ No risk of unauthorized admin creation
- ✅ Secure by design

### Method 2: Environment Variables (Recommended for Production)

**For Docker/Kubernetes deployments:**
```bash
# Set environment variables
export ADMIN_BOOTSTRAP_EMAIL="admin@yourcompany.com"
export ADMIN_BOOTSTRAP_PASSWORD="SuperSecureAdmin123!"

# Start the application - admin is created automatically
docker run -e ADMIN_BOOTSTRAP_EMAIL -e ADMIN_BOOTSTRAP_PASSWORD vernont-backend
```

**For traditional deployments:**
```yaml
# application-prod.yml
nexus:
  admin:
    bootstrap:
      email: admin@yourcompany.com
      password: ${ADMIN_PASSWORD}  # Set via secure env var
```

### Method 3: Command Line Script (Most User-Friendly)

```bash
# Make script executable
chmod +x scripts/create-admin.sh

# Run interactive script
./scripts/create-admin.sh

# Or provide parameters directly
./scripts/create-admin.sh admin@company.com SecurePass123! your-secret-key
```

## 🔐 Security Features

### Bootstrap Protection
- ✅ **Secret Key Required**: Prevents unauthorized admin creation
- ✅ **One-Time Use**: Auto-disables after first admin is created
- ✅ **Admin Detection**: Checks if any admin already exists
- ✅ **Email Validation**: Prevents duplicate email addresses

### Password Security
- ✅ **Minimum 12 Characters**: Stronger than customer passwords
- ✅ **Argon2id Hashing**: Military-grade password protection
- ✅ **Auto-Verification**: Admin accounts are pre-verified

### Audit Trail
- ✅ **Creation Logging**: All admin creation attempts are logged
- ✅ **IP Tracking**: Bootstrap requests include source information
- ✅ **Security Events**: Failed attempts trigger security logs

## 🏗️ Production Deployment Scenarios

### Scenario 1: Docker Container
```dockerfile
# Dockerfile
FROM openjdk:21-jdk
COPY vernont-backend.jar app.jar

# Set admin via environment
ENV ADMIN_BOOTSTRAP_EMAIL=admin@yourcompany.com
ENV ADMIN_BOOTSTRAP_PASSWORD=${ADMIN_PASSWORD}

ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
# Deploy with secure password
docker run -e ADMIN_PASSWORD="$(openssl rand -base64 32)" vernont-backend
```

### Scenario 2: Kubernetes Deployment
```yaml
# k8s-secret.yml
apiVersion: v1
kind: Secret
metadata:
  name: admin-credentials
type: Opaque
data:
  email: YWRtaW5AeW91cmNvbXBhbnkuY29t  # base64 encoded
  password: <base64-encoded-secure-password>

---
# deployment.yml
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
      - name: vernont-backend
        env:
        - name: ADMIN_BOOTSTRAP_EMAIL
          valueFrom:
            secretKeyRef:
              name: admin-credentials
              key: email
        - name: ADMIN_BOOTSTRAP_PASSWORD
          valueFrom:
            secretKeyRef:
              name: admin-credentials
              key: password
```

### Scenario 3: CI/CD Pipeline
```yaml
# .github/workflows/deploy.yml
- name: Deploy with Admin Setup
  env:
    ADMIN_PASSWORD: ${{ secrets.ADMIN_PASSWORD }}
    BOOTSTRAP_SECRET: ${{ secrets.BOOTSTRAP_SECRET }}
  run: |
    # Deploy application
    kubectl apply -f deployment.yml
    
    # Wait for startup
    kubectl wait --for=condition=ready pod -l app=vernont-backend
    
    # Create admin via bootstrap endpoint
    curl -X POST $NEXUS_HOST/admin/bootstrap/create-admin \
      -d '{"email":"admin@company.com","password":"'$ADMIN_PASSWORD'","secretKey":"'$BOOTSTRAP_SECRET'"}'
```

## 👥 Creating Additional Admin/Staff Users

**After the first admin is created, use the admin management endpoints:**

### Create Staff User (Admin Only)
```bash
# Login first to get JWT token
TOKEN=$(curl -X POST /store/auth/login -d '{"email":"admin@company.com","password":"admin-pass"}' | jq -r .accessToken)

# Create customer service rep
curl -X POST /admin/users/create \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "support@company.com",
    "password": "StaffPassword123!",
    "role": "CUSTOMER_SERVICE",
    "firstName": "Support",
    "lastName": "Team"
  }'

# Create warehouse manager
curl -X POST /admin/users/create \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "email": "warehouse@company.com", 
    "password": "WarehousePass123!",
    "role": "WAREHOUSE_MANAGER"
  }'
```

### Available Staff Roles
- **ADMIN**: Full system access
- **CUSTOMER_SERVICE**: Customer support, order management
- **WAREHOUSE_MANAGER**: Inventory, fulfillment management
- **DEVELOPER**: Development, debugging access

## 🔍 Verification & Status

### Check Bootstrap Status
```bash
curl -X GET http://localhost:8080/admin/bootstrap/status
```

**Response when available:**
```json
{
  "bootstrapEnabled": true,
  "adminExists": false,
  "adminCount": 0,
  "message": "Bootstrap available - no admin users exist"
}
```

**Response when disabled:**
```json
{
  "bootstrapEnabled": false,
  "adminExists": true,
  "adminCount": 1,
  "message": "Bootstrap disabled - admin user already created"
}
```

### List Staff Users (Admin Only)
```bash
curl -X GET /admin/users \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

## 🚨 Security Best Practices

### 1. Secret Key Management
```bash
# Generate secure secret key
openssl rand -hex 32

# Store in secure environment variable
export ADMIN_BOOTSTRAP_SECRET="$(openssl rand -hex 32)"
```

### 2. Password Requirements
- ✅ **Minimum 12 characters** for admin accounts
- ✅ **Mix of uppercase, lowercase, numbers, symbols**
- ✅ **No dictionary words or personal information**
- ✅ **Unique per admin user**

### 3. Environment Security
```bash
# Don't expose secrets in logs
set +x  # Disable command echoing

# Use secure methods to pass secrets
export ADMIN_PASSWORD="$(cat /secure/admin-password.txt)"

# Clean up after use
unset ADMIN_PASSWORD
unset ADMIN_BOOTSTRAP_SECRET
```

### 4. Production Checklist
- [ ] Bootstrap secret key is randomly generated (32+ chars)
- [ ] Admin password meets complexity requirements
- [ ] Bootstrap endpoint is disabled after admin creation
- [ ] Admin creation events are logged and monitored
- [ ] Backup admin access method is documented
- [ ] Admin accounts are regularly audited

## 🔧 Troubleshooting

### Bootstrap Not Working
```bash
# Check if admin already exists
curl /admin/bootstrap/status

# Verify secret key
echo $ADMIN_BOOTSTRAP_SECRET

# Check application logs
kubectl logs -f deployment/vernont-backend
```

### Admin Login Issues
```bash
# Verify admin was created
curl /admin/bootstrap/status

# Test login endpoint
curl -X POST /store/auth/login \
  -d '{"email":"admin@company.com","password":"your-password"}'

# Check user in database
kubectl exec -it postgres-pod -- psql -c "SELECT email, roles FROM app_user WHERE email='admin@company.com';"
```

## 🎯 Next Steps

1. **Create your first admin** using one of the methods above
2. **Secure your bootstrap secret** and store it safely
3. **Create additional staff users** via the admin panel
4. **Set up monitoring** for admin access and security events
5. **Document your admin procedures** for your team

Your admin infrastructure is now secure, scalable, and production-ready! 👑