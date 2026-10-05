CREATE TABLE shop_submission_guard (id INTEGER PRIMARY KEY CHECK (id = 1));
INSERT INTO shop_submission_guard (id) VALUES (1);

CREATE TABLE shop_requests (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(36) NOT NULL UNIQUE,
    fingerprint VARCHAR(64) NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('ORDER', 'CONSULTATION')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('NEW', 'PROCESSING', 'CONFIRMED', 'CLOSED')),
    catalog_version VARCHAR(50) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    total_minor BIGINT NOT NULL CHECK (total_minor >= 0),
    items_json TEXT NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    customer_phone VARCHAR(32) NOT NULL,
    customer_telegram VARCHAR(100),
    pickup_city VARCHAR(120),
    pickup_code VARCHAR(40),
    pickup_address VARCHAR(300),
    comment TEXT,
    address_hash VARCHAR(64) NOT NULL,
    phone_hash VARCHAR(64) NOT NULL,
    consent_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CHECK (kind <> 'ORDER' OR (pickup_city IS NOT NULL AND pickup_code IS NOT NULL AND pickup_address IS NOT NULL))
);
CREATE INDEX ix_shop_requests_status ON shop_requests(status, id DESC);
CREATE INDEX ix_shop_requests_address ON shop_requests(address_hash, created_at);
CREATE INDEX ix_shop_requests_phone ON shop_requests(phone_hash, created_at);

CREATE TABLE shop_notifications (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL UNIQUE REFERENCES shop_requests(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('queued', 'sending', 'accepted', 'failed', 'uncertain', 'blocked')),
    attempts INTEGER NOT NULL DEFAULT 0,
    retry_attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(40),
    available_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    leased_until TIMESTAMP,
    lease_key VARCHAR(36),
    chat_id BIGINT
);
CREATE INDEX ix_shop_notifications_ready ON shop_notifications(status, available_at);
