-- V31: Add product_id to order_line_item for review verification

-- Add product_id column
ALTER TABLE order_line_item ADD COLUMN IF NOT EXISTS product_id VARCHAR(36);

-- Create index for product-based queries (used by review verification)
CREATE INDEX IF NOT EXISTS idx_order_line_item_product_id ON order_line_item (product_id);

COMMENT ON COLUMN order_line_item.product_id IS 'Reference to the product for verified purchase checks';
