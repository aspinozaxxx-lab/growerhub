ALTER TABLE zigbee_device_snapshots ADD COLUMN live_state_json TEXT;
ALTER TABLE zigbee_device_snapshots ADD COLUMN last_live_state_at TIMESTAMP;

ALTER TABLE pump_watering_sessions ADD COLUMN executor_type VARCHAR(32) NOT NULL DEFAULT 'NATIVE_PUMP';
ALTER TABLE pump_watering_sessions ADD COLUMN zigbee_coordinator_id INTEGER;
ALTER TABLE pump_watering_sessions ADD COLUMN zigbee_ieee_address VARCHAR(255);
ALTER TABLE pump_watering_sessions ADD COLUMN zigbee_property VARCHAR(255);
ALTER TABLE pump_watering_sessions ADD COLUMN zigbee_on_value VARCHAR(255);
ALTER TABLE pump_watering_sessions ADD COLUMN zigbee_off_value VARCHAR(255);
ALTER TABLE pump_watering_sessions ADD COLUMN executor_key VARCHAR(512);
ALTER TABLE pump_watering_sessions ADD COLUMN run_confirmed_at TIMESTAMP;

CREATE INDEX ix_pump_watering_sessions_executor ON pump_watering_sessions(executor_key, id);

ALTER TABLE pump_watering_sessions ADD CONSTRAINT ck_watering_executor CHECK (
    (executor_type = 'NATIVE_PUMP' AND zigbee_coordinator_id IS NULL AND zigbee_ieee_address IS NULL AND zigbee_property IS NULL)
    OR (executor_type = 'ZIGBEE_DEVICE' AND pump_id IS NULL AND device_id IS NULL
        AND zigbee_coordinator_id IS NOT NULL AND zigbee_ieee_address IS NOT NULL
        AND zigbee_property IS NOT NULL AND zigbee_on_value IS NOT NULL AND zigbee_off_value IS NOT NULL
        AND executor_key IS NOT NULL)
);
