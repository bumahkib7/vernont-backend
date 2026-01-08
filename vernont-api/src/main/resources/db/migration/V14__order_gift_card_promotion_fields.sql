-- Add gift card amount and promotion code fields to orders table
-- These support the new discount/gift card checkout features

ALTER TABLE orders ADD COLUMN IF NOT EXISTS gift_card_amount NUMERIC(19, 4) NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS promotion_code VARCHAR(50);

-- Add index for querying orders by promotion code
CREATE INDEX IF NOT EXISTS idx_orders_promotion_code ON orders(promotion_code) WHERE promotion_code IS NOT NULL;

COMMENT ON COLUMN orders.gift_card_amount IS 'Amount paid with gift cards (in currency units)';
COMMENT ON COLUMN orders.promotion_code IS 'Promotion/discount code applied to this order';
