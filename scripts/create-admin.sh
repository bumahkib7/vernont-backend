#!/bin/bash

# NexusCommerce Admin Creation Script
# Usage: ./create-admin.sh [email] [password] [secret-key]

set -e
# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
DEFAULT_HOST="http://localhost:8080"
HOST="${NEXUS_HOST:-$DEFAULT_HOST}"

echo -e "${BLUE}=== NexusCommerce Admin Creation Tool ===${NC}"
echo ""

# Function to prompt for input with validation
prompt_for_input() {
    local prompt="$1"
    local validation="$2"
    local value=""
    
    while true; do
        echo -n "$prompt"
        read -r value
        
        if [[ -n "$value" && ( -z "$validation" || $value =~ $validation ) ]]; then
            echo "$value"
            break
        else
            echo -e "${RED}Invalid input. Please try again.${NC}"
        fi
    done
}

# Function to prompt for password (hidden input)
prompt_for_password() {
    local prompt="$1"
    local password=""
    
    while true; do
        echo -n "$prompt"
        read -s password
        echo
        
        if [[ ${#password} -ge 12 ]]; then
            echo "$password"
            break
        else
            echo -e "${RED}Password must be at least 12 characters long.${NC}"
        fi
    done
}

# Get input parameters or prompt
if [[ $# -eq 3 ]]; then
    ADMIN_EMAIL="$1"
    ADMIN_PASSWORD="$2"
    SECRET_KEY="$3"
else
    echo -e "${YELLOW}Creating first admin user for NexusCommerce${NC}"
    echo ""
    
    # Check bootstrap status first
    echo "Checking bootstrap status..."
    BOOTSTRAP_STATUS=$(curl -s "${HOST}/bootstrap/status" | grep -o '"bootstrapEnabled":[^,]*' | cut -d':' -f2 || echo "false")
    
    if [[ "$BOOTSTRAP_STATUS" == "false" ]]; then
        echo -e "${RED}Bootstrap is disabled. Admin user already exists.${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}Bootstrap available. You can create the first admin user.${NC}"
    echo ""
    
    # Prompt for inputs
    EMAIL_REGEX="^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$"
    ADMIN_EMAIL=$(prompt_for_input "Enter admin email: " "$EMAIL_REGEX")
    
    ADMIN_PASSWORD=$(prompt_for_password "Enter admin password (min 12 chars): ")
    
    SECRET_KEY=$(prompt_for_input "Enter bootstrap secret key: ")
    
    FIRST_NAME=$(prompt_for_input "Enter first name (optional): ")
    LAST_NAME=$(prompt_for_input "Enter last name (optional): ")
fi

echo ""
echo -e "${BLUE}Creating admin user...${NC}"

# Prepare JSON payload
JSON_PAYLOAD="{
    \"email\": \"$ADMIN_EMAIL\",
    \"password\": \"$ADMIN_PASSWORD\",
    \"secretKey\": \"$SECRET_KEY\""

if [[ -n "$FIRST_NAME" ]]; then
    JSON_PAYLOAD+=",\"firstName\": \"$FIRST_NAME\""
fi

if [[ -n "$LAST_NAME" ]]; then
    JSON_PAYLOAD+=",\"lastName\": \"$LAST_NAME\""
fi

JSON_PAYLOAD+="}"

# Make API call
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d "$JSON_PAYLOAD" \
    "${HOST}/bootstrap/create-admin")

# Parse response
HTTP_CODE=$(printf '%s\n' "$RESPONSE" | tail -n 1)
BODY=$(printf '%s\n' "$RESPONSE" | sed '$d')


if [[ "$HTTP_CODE" == "201" ]]; then
    echo -e "${GREEN}✅ Admin user created successfully!${NC}"
    echo ""
    echo "Admin Details:"
    echo "  Email: $ADMIN_EMAIL"
    echo "  Status: Active"
    echo ""
    echo -e "${BLUE}You can now login at: ${HOST}/store/auth/login${NC}"
    echo ""
    echo -e "${YELLOW}Important: Bootstrap is now disabled. Additional admin users must be created through the admin panel.${NC}"
    
elif [[ "$HTTP_CODE" == "409" ]]; then
    echo -e "${YELLOW}⚠️  Admin user already exists or email is taken.${NC}"
    echo "Details: $(echo "$BODY" | grep -o '"message":"[^"]*' | cut -d'"' -f4)"
    
elif [[ "$HTTP_CODE" == "401" ]]; then
    echo -e "${RED}❌ Invalid secret key.${NC}"
    echo "Please check your bootstrap secret key and try again."
    
elif [[ "$HTTP_CODE" == "403" ]]; then
    echo -e "${RED}❌ Bootstrap is disabled.${NC}"
    echo "Admin user already exists. Use the admin panel to create additional users."
    
else
    echo -e "${RED}❌ Failed to create admin user (HTTP $HTTP_CODE)${NC}"
    echo "Response: $BODY"
    exit 1
fi

echo ""
echo -e "${BLUE}=== Admin Creation Complete ===${NC}"