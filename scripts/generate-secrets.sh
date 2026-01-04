#!/bin/bash

# Generate secure secrets for production

echo "🔐 Generating Secure Secrets for Production"
echo "============================================"
echo ""

echo "JWT_SECRET (256-bit base64):"
JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
echo "$JWT_SECRET"
echo ""

echo "ADMIN_PASSWORD (32-char random):"
ADMIN_PASSWORD=$(openssl rand -base64 32 | tr -d '\n' | cut -c1-32)
echo "$ADMIN_PASSWORD"
echo ""

echo "============================================"
echo "✅ Generated! Copy these to update-beanstalk-env.sh"
echo ""
echo "Edit the script:"
echo "  nano scripts/update-beanstalk-env.sh"
echo ""
echo "Then run:"
echo "  ./scripts/update-beanstalk-env.sh"
