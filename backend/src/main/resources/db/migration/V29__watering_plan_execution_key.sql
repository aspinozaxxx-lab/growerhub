ALTER TABLE pump_watering_sessions ADD COLUMN execution_key VARCHAR(200);
CREATE UNIQUE INDEX uq_pump_watering_execution_key ON pump_watering_sessions(execution_key);
