BEGIN;

ALTER TABLE zigbee_device_snapshots
    ADD COLUMN history_checkpoint_json TEXT NULL;

CREATE TABLE history_retention_state (
    id INTEGER PRIMARY KEY,
    next_day DATE NULL,
    updated_at TIMESTAMP NOT NULL
);

INSERT INTO history_retention_state (id, next_day, updated_at)
VALUES (1, NULL, CURRENT_TIMESTAMP);

CREATE INDEX ix_zigbee_property_readings_state_event
    ON zigbee_device_property_readings(state_event_id);

CREATE INDEX ix_zigbee_state_events_device_snapshot
    ON zigbee_device_state_events(device_snapshot_id);

CREATE INDEX ix_zigbee_property_readings_device_snapshot
    ON zigbee_device_property_readings(device_snapshot_id);

CREATE INDEX ix_sensor_readings_ts_brin
    ON sensor_readings USING BRIN(ts);

CREATE INDEX ix_plant_metric_samples_ts_brin
    ON plant_metric_samples USING BRIN(ts);

CREATE INDEX ix_pump_state_readings_ts_brin
    ON pump_state_readings USING BRIN(ts);

CREATE INDEX ix_zigbee_state_events_ts_brin
    ON zigbee_device_state_events USING BRIN(ts);

CREATE INDEX ix_zigbee_property_readings_ts_brin
    ON zigbee_device_property_readings USING BRIN(ts);

ALTER TABLE sensor_readings SET (
    autovacuum_analyze_scale_factor = 0.02,
    autovacuum_vacuum_insert_scale_factor = 0.05
);

ALTER TABLE plant_metric_samples SET (
    autovacuum_analyze_scale_factor = 0.02,
    autovacuum_vacuum_insert_scale_factor = 0.05
);

ALTER TABLE pump_state_readings SET (
    autovacuum_analyze_scale_factor = 0.02,
    autovacuum_vacuum_insert_scale_factor = 0.05
);

ALTER TABLE zigbee_device_state_events SET (
    autovacuum_analyze_scale_factor = 0.02,
    autovacuum_vacuum_insert_scale_factor = 0.05
);

ALTER TABLE zigbee_device_property_readings SET (
    autovacuum_analyze_scale_factor = 0.02,
    autovacuum_vacuum_insert_scale_factor = 0.05
);

COMMIT;
