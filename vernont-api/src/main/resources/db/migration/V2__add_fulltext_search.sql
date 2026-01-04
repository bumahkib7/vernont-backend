-- Enable pg_trgm extension for similarity search (suggestions)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Add full-text search vector column to product table
ALTER TABLE product ADD COLUMN IF NOT EXISTS search_vector tsvector;

-- Create GIN index for fast full-text search
CREATE INDEX IF NOT EXISTS idx_product_search_vector ON product USING GIN (search_vector);

-- Create function to update search vector
CREATE OR REPLACE FUNCTION product_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', COALESCE(NEW.title, '')), 'A') ||
        setweight(to_tsvector('english', COALESCE(NEW.subtitle, '')), 'B') ||
        setweight(to_tsvector('english', COALESCE(NEW.description, '')), 'C');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to auto-update search vector on insert/update
DROP TRIGGER IF EXISTS product_search_vector_trigger ON product;
CREATE TRIGGER product_search_vector_trigger
    BEFORE INSERT OR UPDATE OF title, subtitle, description
    ON product
    FOR EACH ROW
    EXECUTE FUNCTION product_search_vector_update();

-- Populate search_vector for existing products
UPDATE product SET search_vector =
    setweight(to_tsvector('english', COALESCE(title, '')), 'A') ||
    setweight(to_tsvector('english', COALESCE(subtitle, '')), 'B') ||
    setweight(to_tsvector('english', COALESCE(description, '')), 'C')
WHERE search_vector IS NULL;

-- Create index for brand name search suggestions
CREATE INDEX IF NOT EXISTS idx_brand_name_trgm ON brand USING GIN (name gin_trgm_ops);

-- Create partial index for published products (frequently queried)
CREATE INDEX IF NOT EXISTS idx_product_published ON product (status) WHERE status = 'PUBLISHED' AND deleted_at IS NULL;
