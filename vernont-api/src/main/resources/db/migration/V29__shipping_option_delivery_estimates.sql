-- Add delivery estimate fields to shipping_option table
ALTER TABLE shipping_option
    ADD COLUMN IF NOT EXISTS estimated_days_min INTEGER,
    ADD COLUMN IF NOT EXISTS estimated_days_max INTEGER,
    ADD COLUMN IF NOT EXISTS carrier VARCHAR(100);

-- Add comments for documentation
COMMENT ON COLUMN shipping_option.estimated_days_min IS 'Minimum estimated delivery days from order placement';
COMMENT ON COLUMN shipping_option.estimated_days_max IS 'Maximum estimated delivery days from order placement';
COMMENT ON COLUMN shipping_option.carrier IS 'Carrier name (e.g., Royal Mail, DHL, UPS)';

-- Update existing shipping options with default values
UPDATE shipping_option
SET estimated_days_min = 3,
    estimated_days_max = 5
WHERE name ILIKE '%standard%' AND estimated_days_min IS NULL;

UPDATE shipping_option
SET estimated_days_min = 1,
    estimated_days_max = 2
WHERE name ILIKE '%express%' AND estimated_days_min IS NULL;

UPDATE shipping_option
SET estimated_days_min = 1,
    estimated_days_max = 1
WHERE name ILIKE '%next%day%' AND estimated_days_min IS NULL;
