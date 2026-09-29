ALTER TABLE zigbee_coordinators ADD COLUMN transport_kind VARCHAR(32) NOT NULL DEFAULT 'ZIGBEE2MQTT';

CREATE TABLE zigbee_pushok_connections (
    coordinator_id INTEGER PRIMARY KEY REFERENCES zigbee_coordinators(id) ON DELETE CASCADE,
    hub_id VARCHAR(80) NOT NULL UNIQUE,
    encrypted_credentials TEXT NOT NULL,
    hub_public_key VARCHAR(256),
    status VARCHAR(32) NOT NULL,
    last_error VARCHAR(64),
    attempt_at TIMESTAMP NOT NULL
);
