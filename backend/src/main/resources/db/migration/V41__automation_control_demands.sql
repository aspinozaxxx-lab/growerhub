CREATE TABLE automation_control_demands (
    id UUID PRIMARY KEY, user_id INTEGER NOT NULL, resource_id INTEGER NOT NULL,
    target_key VARCHAR(255) NOT NULL, binding_fingerprint VARCHAR(255) NOT NULL,
    source_type VARCHAR(255) NOT NULL, source_id VARCHAR(255) NOT NULL,
    source_key VARCHAR(255) NOT NULL, source_name VARCHAR(255) NOT NULL,
    reason TEXT, revision INTEGER NOT NULL, priority INTEGER NOT NULL, value_json TEXT NOT NULL,
    kind VARCHAR(255) NOT NULL, status VARCHAR(255) NOT NULL, source_fingerprint VARCHAR(255),
    requested_at TIMESTAMP NOT NULL, created_at TIMESTAMP NOT NULL, duration_seconds INTEGER, confirmed_at TIMESTAMP,
    expires_at TIMESTAMP, valid_until TIMESTAMP, ack_deadline_at TIMESTAMP, interrupted BOOLEAN NOT NULL DEFAULT FALSE, error TEXT,
    UNIQUE(user_id, source_key), FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX ix_control_demands_owner_target ON automation_control_demands(user_id, target_key);
CREATE INDEX ix_control_demands_status ON automation_control_demands(status);
CREATE TABLE automation_control_channels (
    id UUID PRIMARY KEY, user_id INTEGER NOT NULL, resource_id INTEGER NOT NULL,
    target_key VARCHAR(255) NOT NULL, binding_fingerprint VARCHAR(255) NOT NULL,
    coordinator_id UUID NOT NULL, ieee_address VARCHAR(255) NOT NULL, property VARCHAR(255) NOT NULL,
    winner_id UUID, revision INTEGER NOT NULL, command_id UUID, value_json TEXT,
    status VARCHAR(255) NOT NULL, requested_at TIMESTAMP, dispatch_at TIMESTAMP,
    ack_deadline_at TIMESTAMP, confirmed_at TIMESTAMP, error TEXT,
    UNIQUE(user_id, target_key), FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX ix_control_channels_status ON automation_control_channels(status);
ALTER TABLE zigbee_device_snapshots ADD COLUMN live_properties_json TEXT;
