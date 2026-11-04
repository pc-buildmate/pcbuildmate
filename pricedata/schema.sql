-- pricedata schema v1 (SQLite)
-- 详细字段解释见 pricedata/README.md §4

PRAGMA foreign_keys = ON;

CREATE TABLE products (
    product_id      TEXT PRIMARY KEY,
    category        TEXT NOT NULL,
    brand           TEXT NOT NULL,
    model           TEXT NOT NULL,
    part_number     TEXT,
    release_year    INTEGER NOT NULL,
    platform        TEXT,
    integrated_graphics TEXT CHECK (integrated_graphics IN ('YES', 'NO') OR integrated_graphics IS NULL),
    status          TEXT NOT NULL CHECK (status IN ('active', 'legacy', 'eol')),
    recommendable   INTEGER NOT NULL CHECK (recommendable IN (0, 1)),
    specs_json      TEXT NOT NULL,
    created_at      TEXT NOT NULL,
    updated_at      TEXT NOT NULL
);

CREATE TABLE prices (
    observation_id  TEXT PRIMARY KEY,
    product_id      TEXT NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
    observed_at     TEXT NOT NULL,
    price_cny       INTEGER NOT NULL CHECK (price_cny > 0),
    list_price_cny  INTEGER CHECK (list_price_cny IS NULL OR list_price_cny > 0),
    promo_price_cny INTEGER CHECK (promo_price_cny IS NULL OR promo_price_cny > 0),
    channel         TEXT NOT NULL,
    condition       TEXT NOT NULL CHECK (condition IN ('new', 'used')),
    source_url      TEXT NOT NULL,
    source_type     TEXT NOT NULL CHECK (source_type IN ('official', 'wayback', 'manual', 'marketplace')),
    confidence      TEXT NOT NULL CHECK (confidence IN ('high', 'medium', 'low')),
    captured_by     TEXT,
    notes           TEXT,
    UNIQUE (product_id, observed_at, channel, condition, source_type)
);

CREATE TABLE sources (
    source_id       TEXT PRIMARY KEY,
    name            TEXT NOT NULL,
    url             TEXT NOT NULL,
    source_type     TEXT NOT NULL CHECK (source_type IN ('official', 'wayback', 'manual', 'marketplace')),
    enabled         INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    min_interval_s  INTEGER NOT NULL DEFAULT 3600,
    notes           TEXT
);

CREATE INDEX idx_prices_product_date ON prices(product_id, observed_at);
CREATE INDEX idx_prices_channel_date ON prices(channel, observed_at);
CREATE INDEX idx_products_category_status ON products(category, status);
