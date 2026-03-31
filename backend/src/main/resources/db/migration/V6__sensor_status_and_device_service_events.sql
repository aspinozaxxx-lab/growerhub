BEGIN;

ALTER TABLE sensors ADD COLUMN IF NOT EXISTS status VARCHAR(32) NULL;
ALTER TABLE sensors ADD COLUMN IF NOT EXISTS status_changed_at TIMESTAMP NULL;
ALTER TABLE sensors ADD COLUMN IF NOT EXISTS last_error_at TIMESTAMP NULL;

CREATE TABLE IF NOT EXISTS device_service_events (
    id SERIAL PRIMARY KEY,
    device_id INTEGER NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    event_type VARCHAR(64) NOT NULL,
    sensor_scope VARCHAR(64) NULL,
    sensor_type VARCHAR(64) NULL,
    channel INTEGER NULL,
    failure_id VARCHAR(128) NULL,
    error_code VARCHAR(128) NULL,
    event_at TIMESTAMP NULL,
    received_at TIMESTAMP NOT NULL,
    payload_json TEXT NULL
);

CREATE INDEX IF NOT EXISTS ix_device_service_events_device_received
    ON device_service_events (device_id, received_at);

COMMIT;
