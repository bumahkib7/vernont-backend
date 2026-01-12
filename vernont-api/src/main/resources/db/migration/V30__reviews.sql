-- V30: Product Reviews and Ratings System
-- Amazon-style review system with voting, reporting, and moderation

-- ============================================================================
-- PRODUCT_REVIEW - Customer product reviews
-- ============================================================================

CREATE TABLE IF NOT EXISTS product_review (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_by VARCHAR(255),
    metadata JSONB,
    version BIGINT NOT NULL DEFAULT 0,

    -- Review content
    product_id VARCHAR(36) NOT NULL,
    customer_id VARCHAR(36) NOT NULL,
    customer_name VARCHAR(255) NOT NULL,
    customer_avatar VARCHAR(500),
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    pros JSONB, -- Array of pros as JSONB
    cons JSONB, -- Array of cons as JSONB

    -- Images stored as JSONB array: [{id, url, thumbnailUrl, caption, sortOrder}]
    images JSONB,

    -- Purchase verification
    verified_purchase BOOLEAN NOT NULL DEFAULT FALSE,
    order_id VARCHAR(36),
    variant_id VARCHAR(36),
    variant_title VARCHAR(255),

    -- Voting counts (denormalized for performance)
    helpful_count INTEGER NOT NULL DEFAULT 0,
    not_helpful_count INTEGER NOT NULL DEFAULT 0,
    report_count INTEGER NOT NULL DEFAULT 0,

    -- Moderation
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'FLAGGED', 'HIDDEN')),
    moderated_at TIMESTAMP,
    moderated_by VARCHAR(36),
    moderation_note TEXT,

    -- Featured/Pinned
    is_featured BOOLEAN NOT NULL DEFAULT FALSE,

    -- Edit tracking
    is_edited BOOLEAN NOT NULL DEFAULT FALSE,
    edited_at TIMESTAMP,

    -- Admin response
    admin_response TEXT,
    admin_response_at TIMESTAMP,
    admin_response_by VARCHAR(36)
);

-- Unique constraint: one review per customer per product
CREATE UNIQUE INDEX IF NOT EXISTS uk_product_review_customer_product
    ON product_review (product_id, customer_id)
    WHERE deleted_at IS NULL;

-- Product reviews (approved only, for public display)
CREATE INDEX IF NOT EXISTS idx_product_review_product_approved
    ON product_review (product_id, status, created_at DESC)
    WHERE deleted_at IS NULL AND status = 'APPROVED';

-- Customer reviews
CREATE INDEX IF NOT EXISTS idx_product_review_customer
    ON product_review (customer_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- Moderation queue (pending reviews)
CREATE INDEX IF NOT EXISTS idx_product_review_pending
    ON product_review (status, created_at ASC)
    WHERE deleted_at IS NULL AND status = 'PENDING';

-- Flagged reviews for moderation
CREATE INDEX IF NOT EXISTS idx_product_review_flagged
    ON product_review (status, report_count DESC)
    WHERE deleted_at IS NULL AND (status = 'FLAGGED' OR report_count > 0);

-- Featured reviews
CREATE INDEX IF NOT EXISTS idx_product_review_featured
    ON product_review (product_id, is_featured, status)
    WHERE deleted_at IS NULL AND is_featured = TRUE AND status = 'APPROVED';

-- Rating filter
CREATE INDEX IF NOT EXISTS idx_product_review_rating
    ON product_review (product_id, rating, status)
    WHERE deleted_at IS NULL AND status = 'APPROVED';

-- Helpful count for sorting
CREATE INDEX IF NOT EXISTS idx_product_review_helpful
    ON product_review (product_id, helpful_count DESC)
    WHERE deleted_at IS NULL AND status = 'APPROVED';

COMMENT ON TABLE product_review IS 'Customer product reviews with ratings';
COMMENT ON COLUMN product_review.status IS 'PENDING=awaiting moderation, APPROVED=visible, REJECTED=hidden, FLAGGED=needs review, HIDDEN=admin hidden';

-- ============================================================================
-- REVIEW_VOTE - Helpful/Not Helpful votes
-- ============================================================================

CREATE TABLE IF NOT EXISTS review_vote (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_by VARCHAR(255),
    metadata JSONB,
    version BIGINT NOT NULL DEFAULT 0,

    review_id VARCHAR(36) NOT NULL REFERENCES product_review(id) ON DELETE CASCADE,
    customer_id VARCHAR(36) NOT NULL,
    vote_type VARCHAR(20) NOT NULL CHECK (vote_type IN ('HELPFUL', 'NOT_HELPFUL'))
);

-- Unique constraint: one vote per customer per review
CREATE UNIQUE INDEX IF NOT EXISTS uk_review_vote_customer_review
    ON review_vote (review_id, customer_id)
    WHERE deleted_at IS NULL;

-- Count votes by type
CREATE INDEX IF NOT EXISTS idx_review_vote_review_type
    ON review_vote (review_id, vote_type)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE review_vote IS 'Customer votes on review helpfulness';
COMMENT ON COLUMN review_vote.vote_type IS 'HELPFUL or NOT_HELPFUL';

-- ============================================================================
-- PRODUCT_REVIEW_STATS - Cached aggregated stats per product
-- ============================================================================

CREATE TABLE IF NOT EXISTS product_review_stats (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_by VARCHAR(255),
    metadata JSONB,
    version BIGINT NOT NULL DEFAULT 0,

    product_id VARCHAR(36) NOT NULL,

    -- Aggregate stats
    average_rating DECIMAL(3, 2) NOT NULL DEFAULT 0,
    total_reviews INTEGER NOT NULL DEFAULT 0,
    verified_purchase_count INTEGER NOT NULL DEFAULT 0,
    with_images_count INTEGER NOT NULL DEFAULT 0,

    -- Rating distribution (count per star)
    one_star_count INTEGER NOT NULL DEFAULT 0,
    two_star_count INTEGER NOT NULL DEFAULT 0,
    three_star_count INTEGER NOT NULL DEFAULT 0,
    four_star_count INTEGER NOT NULL DEFAULT 0,
    five_star_count INTEGER NOT NULL DEFAULT 0,

    -- Rating percentages (pre-calculated)
    one_star_percent INTEGER NOT NULL DEFAULT 0,
    two_star_percent INTEGER NOT NULL DEFAULT 0,
    three_star_percent INTEGER NOT NULL DEFAULT 0,
    four_star_percent INTEGER NOT NULL DEFAULT 0,
    five_star_percent INTEGER NOT NULL DEFAULT 0,

    -- Recommendation rate (% of 4+ star reviews)
    recommendation_percent INTEGER NOT NULL DEFAULT 0,

    last_review_at TIMESTAMP,
    last_calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Unique constraint: one stats row per product
CREATE UNIQUE INDEX IF NOT EXISTS uk_product_review_stats_product
    ON product_review_stats (product_id)
    WHERE deleted_at IS NULL;

-- Top rated products query
CREATE INDEX IF NOT EXISTS idx_product_review_stats_rating
    ON product_review_stats (average_rating DESC, total_reviews DESC)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE product_review_stats IS 'Cached aggregated review statistics per product';
COMMENT ON COLUMN product_review_stats.recommendation_percent IS 'Percentage of reviews with 4+ stars';

-- ============================================================================
-- REVIEW_REPORT - Flagging inappropriate reviews
-- ============================================================================

CREATE TABLE IF NOT EXISTS review_report (
    id VARCHAR(36) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_by VARCHAR(255),
    metadata JSONB,
    version BIGINT NOT NULL DEFAULT 0,

    review_id VARCHAR(36) NOT NULL REFERENCES product_review(id) ON DELETE CASCADE,
    customer_id VARCHAR(36) NOT NULL,
    reason VARCHAR(30) NOT NULL CHECK (reason IN ('SPAM', 'OFFENSIVE', 'IRRELEVANT', 'FAKE', 'INAPPROPRIATE', 'OTHER')),
    description TEXT,

    -- Report resolution
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'REVIEWED', 'ACTIONED', 'DISMISSED')),
    resolved_at TIMESTAMP,
    resolved_by VARCHAR(36),
    resolution_note TEXT
);

-- Unique constraint: one report per customer per review
CREATE UNIQUE INDEX IF NOT EXISTS uk_review_report_customer_review
    ON review_report (review_id, customer_id)
    WHERE deleted_at IS NULL;

-- Pending reports for moderation
CREATE INDEX IF NOT EXISTS idx_review_report_pending
    ON review_report (status, created_at ASC)
    WHERE deleted_at IS NULL AND status = 'PENDING';

-- Reports by review
CREATE INDEX IF NOT EXISTS idx_review_report_review
    ON review_report (review_id)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE review_report IS 'Reports of inappropriate review content';
COMMENT ON COLUMN review_report.reason IS 'SPAM, OFFENSIVE, IRRELEVANT, FAKE, INAPPROPRIATE, OTHER';
COMMENT ON COLUMN review_report.status IS 'PENDING=needs review, REVIEWED=seen, ACTIONED=action taken, DISMISSED=no action';

-- ============================================================================
-- HELPER FUNCTION: Calculate Wilson score for review ranking
-- ============================================================================

CREATE OR REPLACE FUNCTION calculate_wilson_score(positive INTEGER, negative INTEGER)
RETURNS DECIMAL(10, 8) AS $$
DECLARE
    n INTEGER;
    p DECIMAL;
    z DECIMAL := 1.96; -- 95% confidence
    score DECIMAL;
BEGIN
    n := positive + negative;
    IF n = 0 THEN
        RETURN 0;
    END IF;

    p := positive::DECIMAL / n;

    -- Wilson score interval lower bound
    score := (p + z*z/(2*n) - z * sqrt((p*(1-p) + z*z/(4*n))/n)) / (1 + z*z/n);

    RETURN GREATEST(score, 0);
END;
$$ LANGUAGE plpgsql IMMUTABLE;

COMMENT ON FUNCTION calculate_wilson_score IS 'Calculates Wilson score lower bound for review ranking';
