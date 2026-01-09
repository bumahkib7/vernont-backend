-- V20: Update product status constraint to include new workflow statuses
-- Adds: PENDING_ASSETS, READY, FAILED, ARCHIVED

-- Drop the existing constraint (Postgres auto-named it based on column)
ALTER TABLE product DROP CONSTRAINT IF EXISTS product_status_check;

-- Add the updated constraint with all status values
ALTER TABLE product ADD CONSTRAINT product_status_check
    CHECK (status IN ('DRAFT', 'PENDING_ASSETS', 'PROPOSED', 'READY', 'PUBLISHED', 'REJECTED', 'FAILED', 'ARCHIVED'));

COMMENT ON COLUMN product.status IS 'Product lifecycle status: DRAFT (initial), PENDING_ASSETS (uploading), READY (can publish), PUBLISHED (live), PROPOSED/REJECTED (affiliate), FAILED (error), ARCHIVED (hidden)';
