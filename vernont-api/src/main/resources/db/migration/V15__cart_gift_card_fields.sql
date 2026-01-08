-- Add gift card fields to cart table
ALTER TABLE cart ADD COLUMN IF NOT EXISTS gift_card_code VARCHAR(19);
ALTER TABLE cart ADD COLUMN IF NOT EXISTS gift_card_total NUMERIC(19, 4) NOT NULL DEFAULT 0;

-- Index for looking up carts by gift card code
CREATE INDEX IF NOT EXISTS idx_cart_gift_card_code ON cart(gift_card_code) WHERE gift_card_code IS NOT NULL;

COMMENT ON COLUMN cart.gift_card_code IS 'Applied gift card code (XXXX-XXXX-XXXX-XXXX format)';
COMMENT ON COLUMN cart.gift_card_total IS 'Gift card amount to deduct from total (in major currency units)';
