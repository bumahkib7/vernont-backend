#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIGRATIONS_DIR="$ROOT_DIR/vernont-api/src/main/resources/db/migration"
BASELINE_FILE="$MIGRATIONS_DIR/V1__baseline.sql"
EXTRAS_FILE="$ROOT_DIR/scripts/migration-extras.sql"

mkdir -p "$MIGRATIONS_DIR"

echo "Generating baseline migration from JPA entities..."
(cd "$ROOT_DIR" && ./gradlew :vernont-domain:generateSchema -PoutputFile="$BASELINE_FILE")

echo "Converting camelCase columns to snake_case..."

# Use Perl to convert camelCase to snake_case for all column names
# This handles all cases automatically, matching Hibernate's physical naming strategy
perl -i -pe '
  # Convert camelCase column names to snake_case
  # Match word boundaries to avoid changing SQL keywords
  s/\b([a-z]+)([A-Z])([a-z]+)\b/$1_\l$2$3/g while /\b[a-z]+[A-Z][a-z]+\b/;
' "$BASELINE_FILE"

# Fix specific patterns that the above might miss
if [[ "$OSTYPE" == "darwin"* ]]; then
  SED_INPLACE=(-i '')
else
  SED_INPLACE=(-i)
fi

# Audit log fields
sed "${SED_INPLACE[@]}" 's/entityId/entity_id/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/entityType/entity_type/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/ipAddress/ip_address/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/newValue/new_value/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/oldValue/old_value/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/userAgent/user_agent/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/userId/user_id/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/userName/user_name/g' "$BASELINE_FILE"

# BaseEntity fields
sed "${SED_INPLACE[@]}" 's/createdAt/created_at/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/createdBy/created_by/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/updatedAt/updated_at/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/updatedBy/updated_by/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/deletedAt/deleted_at/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/deletedBy/deleted_by/g' "$BASELINE_FILE"

# User/Customer fields
sed "${SED_INPLACE[@]}" 's/firstName/first_name/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/lastName/last_name/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/lastLoginAt/last_login_at/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/lastOrderAt/last_order_at/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/orderCount/order_count/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/totalSpent/total_spent/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/hasAccount/has_account/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/internalNotes/internal_notes/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/suspendedAt/suspended_at/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/suspendedReason/suspended_reason/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/tierOverride/tier_override/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/billingAddressId/billing_address_id/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/emailVerified/email_verified/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/avatarUrl/avatar_url/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/isActive/is_active/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/passwordHash/password_hash/g' "$BASELINE_FILE"

# Cart fields
sed "${SED_INPLACE[@]}" 's/completedAt/completed_at/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/discountTotal/discount_total/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/shippingMethodId/shipping_method_id/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/paymentMethodId/payment_method_id/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/taxAmount/tax_amount/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/taxRate/tax_rate/g' "$BASELINE_FILE"

# Cart line item fields
sed "${SED_INPLACE[@]}" 's/allowDiscounts/allow_discounts/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/hasShipping/has_shipping/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/isGiftcard/is_giftcard/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/shouldMerge/should_merge/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/unitPrice/unit_price/g' "$BASELINE_FILE"

# Fix country table constraint references
sed "${SED_INPLACE[@]}" 's/unique (iso2)/unique (iso_2)/g' "$BASELINE_FILE"
sed "${SED_INPLACE[@]}" 's/unique (iso3)/unique (iso_3)/g' "$BASELINE_FILE"

if [ -f "$EXTRAS_FILE" ]; then
  echo "Appending custom extras from $EXTRAS_FILE..."
  {
    echo ""
    echo "-- Custom extras (partials/expressions/JSONB, etc.)"
    cat "$EXTRAS_FILE"
  } >> "$BASELINE_FILE"
fi

echo "Baseline migration generated at $BASELINE_FILE."
echo ""
echo "Post-processing applied:"
echo "  - Converted camelCase columns to snake_case"
echo "  - Fixed iso2/iso3 -> iso_2/iso_3 in country constraints"
