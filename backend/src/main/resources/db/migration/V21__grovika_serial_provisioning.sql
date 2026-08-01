ALTER TABLE devices ADD COLUMN mqtt_provisioned_at TIMESTAMP NULL;

CREATE TABLE device_claim_limits (
    user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    restricted BOOLEAN NOT NULL DEFAULT FALSE,
    blocked_until TIMESTAMP NULL,
    window_started_at TIMESTAMP NULL,
    window_attempts INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL
);
