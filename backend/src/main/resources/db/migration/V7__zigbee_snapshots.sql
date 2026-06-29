BEGIN;

CREATE TABLE IF NOT EXISTS zigbee_bridge_snapshots (
    id INTEGER PRIMARY KEY,
    base_topic VARCHAR(128) NOT NULL,
    state VARCHAR(32) NULL,
    info_json TEXT NULL,
    devices_json TEXT NULL,
    coordinator_ieee_address VARCHAR(32) NULL,
    coordinator_json TEXT NULL,
    permit_join BOOLEAN NULL,
    permit_join_end BIGINT NULL,
    version VARCHAR(64) NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS zigbee_device_snapshots (
    id SERIAL PRIMARY KEY,
    ieee_address VARCHAR(32) NULL,
    friendly_name VARCHAR(255) NOT NULL,
    device_type VARCHAR(64) NULL,
    supported BOOLEAN NULL,
    disabled BOOLEAN NULL,
    coordinator BOOLEAN NOT NULL DEFAULT FALSE,
    bridge_device_json TEXT NULL,
    state_json TEXT NULL,
    availability VARCHAR(32) NULL,
    last_state_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_zigbee_device_snapshots_ieee
    ON zigbee_device_snapshots (ieee_address)
    WHERE ieee_address IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_zigbee_device_snapshots_friendly
    ON zigbee_device_snapshots (friendly_name);

CREATE TABLE IF NOT EXISTS zigbee_command_response_snapshots (
    id INTEGER PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    status VARCHAR(32) NULL,
    error TEXT NULL,
    response_json TEXT NULL,
    updated_at TIMESTAMP NOT NULL
);

COMMIT;
