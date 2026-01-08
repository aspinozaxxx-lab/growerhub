BEGIN;

ALTER TABLE devices DROP COLUMN IF EXISTS soil_moisture;
ALTER TABLE devices DROP COLUMN IF EXISTS air_temperature;
ALTER TABLE devices DROP COLUMN IF EXISTS air_humidity;
ALTER TABLE devices DROP COLUMN IF EXISTS is_watering;
ALTER TABLE devices DROP COLUMN IF EXISTS is_light_on;
ALTER TABLE devices DROP COLUMN IF EXISTS last_watering;
ALTER TABLE devices DROP COLUMN IF EXISTS watering_speed_lph;

DROP TABLE IF EXISTS sensor_data;
DROP TABLE IF EXISTS plant_devices;

CREATE TABLE sensors (
    id SERIAL PRIMARY KEY,
    device_id INTEGER NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    type VARCHAR(64) NOT NULL,
    channel INTEGER NOT NULL,
    label VARCHAR(255) NULL,
    detected BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NULL DEFAULT NOW(),
    updated_at TIMESTAMP NULL DEFAULT NOW(),
    CONSTRAINT uq_sensors_device_type_channel UNIQUE (device_id, type, channel)
);

CREATE TABLE sensor_readings (
    id SERIAL PRIMARY KEY,
    sensor_id INTEGER NOT NULL REFERENCES sensors(id) ON DELETE CASCADE,
    ts TIMESTAMP NOT NULL,
    value_numeric DOUBLE PRECISION NULL,
    created_at TIMESTAMP NULL DEFAULT NOW()
);

CREATE INDEX ix_sensor_readings_sensor_id_ts ON sensor_readings (sensor_id, ts);

CREATE TABLE sensor_plant_bindings (
    id SERIAL PRIMARY KEY,
    sensor_id INTEGER NOT NULL REFERENCES sensors(id) ON DELETE CASCADE,
    plant_id INTEGER NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    CONSTRAINT uq_sensor_plant_bindings_pair UNIQUE (sensor_id, plant_id)
);

CREATE INDEX ix_sensor_plant_bindings_plant_id ON sensor_plant_bindings (plant_id);

CREATE TABLE pumps (
    id SERIAL PRIMARY KEY,
    device_id INTEGER NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    channel INTEGER NOT NULL,
    label VARCHAR(255) NULL,
    created_at TIMESTAMP NULL DEFAULT NOW(),
    updated_at TIMESTAMP NULL DEFAULT NOW(),
    CONSTRAINT uq_pumps_device_channel UNIQUE (device_id, channel)
);

CREATE TABLE pump_plant_bindings (
    id SERIAL PRIMARY KEY,
    pump_id INTEGER NOT NULL REFERENCES pumps(id) ON DELETE CASCADE,
    plant_id INTEGER NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    rate_ml_per_hour INTEGER NOT NULL DEFAULT 2000,
    CONSTRAINT uq_pump_plant_bindings_pair UNIQUE (pump_id, plant_id)
);

CREATE INDEX ix_pump_plant_bindings_plant_id ON pump_plant_bindings (plant_id);

CREATE TABLE plant_metric_samples (
    id SERIAL PRIMARY KEY,
    plant_id INTEGER NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    metric_type VARCHAR(64) NOT NULL,
    ts TIMESTAMP NOT NULL,
    value_numeric DOUBLE PRECISION NULL,
    created_at TIMESTAMP NULL DEFAULT NOW()
);

CREATE INDEX ix_plant_metric_samples_plant_metric_ts ON plant_metric_samples (plant_id, metric_type, ts);

COMMIT;
