CREATE TABLE telegram_channels (
    user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    chat_id BIGINT UNIQUE,
    name VARCHAR(255),
    link_hash VARCHAR(64) UNIQUE,
    link_expires_at TIMESTAMP,
    pending_chat_id BIGINT UNIQUE,
    pending_name VARCHAR(255),
    confirmation VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    digest_hour INTEGER NOT NULL,
    quiet_from INTEGER NOT NULL,
    quiet_until INTEGER NOT NULL,
    address_version BIGINT NOT NULL DEFAULT 0,
    last_status VARCHAR(255),
    last_test_at TIMESTAMP
);
CREATE TABLE telegram_deliveries (
    id BIGSERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chat_id BIGINT NOT NULL,
    address_version BIGINT NOT NULL,
    dedupe_key VARCHAR(255) NOT NULL UNIQUE,
    text TEXT NOT NULL,
    keyboard TEXT,
    status VARCHAR(255) NOT NULL,
    available_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    leased_until TIMESTAMP,
    lease_key VARCHAR(255),
    attempts INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX ix_telegram_delivery_ready ON telegram_deliveries(status, available_at);
CREATE TABLE telegram_updates (id BIGINT PRIMARY KEY, received_at TIMESTAMP NOT NULL);
