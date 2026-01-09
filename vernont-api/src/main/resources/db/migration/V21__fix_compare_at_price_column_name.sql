-- V21: Standardize compare_at_price column name
-- Production has 'compareatprice' (Hibernate-generated), dev has 'compare_at_price' (from baseline)
-- This migration renames to the baseline standard: compare_at_price

-- Only rename if the old column exists (production)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'product_variant_price'
        AND column_name = 'compareatprice'
    ) THEN
        ALTER TABLE product_variant_price RENAME COLUMN compareatprice TO compare_at_price;
    END IF;
END $$;
