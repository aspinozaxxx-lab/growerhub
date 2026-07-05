BEGIN;

CREATE TABLE IF NOT EXISTS zigbee_device_state_events (
    id SERIAL PRIMARY KEY,
    device_snapshot_id INTEGER NULL REFERENCES zigbee_device_snapshots(id) ON DELETE SET NULL,
    ieee_address VARCHAR(32) NULL,
    friendly_name VARCHAR(255) NOT NULL,
    ts TIMESTAMP NOT NULL,
    raw_state_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_zigbee_state_events_ieee_ts
    ON zigbee_device_state_events (ieee_address, ts);

CREATE INDEX IF NOT EXISTS ix_zigbee_state_events_friendly_ts
    ON zigbee_device_state_events (friendly_name, ts);

CREATE TABLE IF NOT EXISTS zigbee_device_property_readings (
    id SERIAL PRIMARY KEY,
    state_event_id INTEGER NOT NULL REFERENCES zigbee_device_state_events(id) ON DELETE CASCADE,
    device_snapshot_id INTEGER NULL REFERENCES zigbee_device_snapshots(id) ON DELETE SET NULL,
    ieee_address VARCHAR(32) NULL,
    friendly_name VARCHAR(255) NOT NULL,
    property VARCHAR(128) NOT NULL,
    ts TIMESTAMP NOT NULL,
    value_numeric DOUBLE PRECISION NULL,
    value_text TEXT NULL,
    value_boolean BOOLEAN NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_zigbee_property_readings_ieee_property_ts
    ON zigbee_device_property_readings (ieee_address, property, ts);

CREATE INDEX IF NOT EXISTS ix_zigbee_property_readings_friendly_property_ts
    ON zigbee_device_property_readings (friendly_name, property, ts);

CREATE TABLE IF NOT EXISTS pump_state_readings (
    id SERIAL PRIMARY KEY,
    pump_id INTEGER NOT NULL REFERENCES pumps(id) ON DELETE CASCADE,
    ts TIMESTAMP NOT NULL,
    is_running BOOLEAN NULL,
    raw_status VARCHAR(64) NULL,
    raw_state_json TEXT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_pump_state_readings_pump_ts
    ON pump_state_readings (pump_id, ts);

COMMIT;
