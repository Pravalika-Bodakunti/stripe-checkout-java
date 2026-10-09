CREATE TABLE IF NOT EXISTS orders (
    id                VARCHAR(36)  PRIMARY KEY,
    amount            BIGINT       NOT NULL,   -- minor units (cents), never floating point
    currency          VARCHAR(3)   NOT NULL,
    status            VARCHAR(16)  NOT NULL,   -- pending | paid | failed
    payment_intent_id VARCHAR(255) UNIQUE
);

-- Stripe may deliver the same event more than once; remember what we've handled.
CREATE TABLE IF NOT EXISTS processed_events (
    id VARCHAR(255) PRIMARY KEY
);
