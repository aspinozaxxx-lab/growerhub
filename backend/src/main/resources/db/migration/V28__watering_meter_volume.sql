ALTER TABLE pump_watering_sessions ADD COLUMN measured_water_volume_l DOUBLE PRECISION;
ALTER TABLE pump_watering_sessions ADD COLUMN water_meter_observed_at TIMESTAMP;
ALTER TABLE pump_watering_sessions ADD COLUMN confirmed_pulse_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE pump_watering_sessions ADD COLUMN metered_pulse_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE plant_journal_watering_details ADD COLUMN volume_source VARCHAR(20);
ALTER TABLE pump_watering_sessions ADD CONSTRAINT ck_watering_measured_volume
    CHECK (measured_water_volume_l IS NULL OR measured_water_volume_l >= 0);
