CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    username VARCHAR(255) NULL,
    role VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE user_auth_identities (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id),
    provider VARCHAR(255) NOT NULL,
    provider_subject VARCHAR(255) NULL,
    password_hash VARCHAR(255) NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    CONSTRAINT uq_user_auth_identities_provider_subject
        UNIQUE (provider, provider_subject),
    CONSTRAINT uq_user_auth_identities_user_provider
        UNIQUE (user_id, provider)
);

CREATE TABLE user_refresh_tokens (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    user_agent OID NULL,
    ip VARCHAR(255) NULL,
    CONSTRAINT uq_user_refresh_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_user_refresh_tokens_user_id
    ON user_refresh_tokens(user_id);
CREATE INDEX ix_user_refresh_tokens_expires_at
    ON user_refresh_tokens(expires_at);

CREATE TABLE devices (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR(255) NULL,
    name VARCHAR(255) NULL,
    user_id INTEGER NULL REFERENCES users(id) ON DELETE SET NULL,
    soil_moisture DOUBLE PRECISION NULL,
    air_temperature DOUBLE PRECISION NULL,
    air_humidity DOUBLE PRECISION NULL,
    is_watering BOOLEAN NULL,
    is_light_on BOOLEAN NULL,
    last_watering TIMESTAMP NULL,
    last_seen TIMESTAMP NULL,
    target_moisture DOUBLE PRECISION NULL,
    watering_duration INTEGER NULL,
    watering_timeout INTEGER NULL,
    watering_speed_lph DOUBLE PRECISION NULL,
    light_on_hour INTEGER NULL,
    light_off_hour INTEGER NULL,
    light_duration INTEGER NULL,
    current_version VARCHAR(255) NULL,
    latest_version VARCHAR(255) NULL,
    update_available BOOLEAN NULL,
    firmware_url VARCHAR(255) NULL
);

CREATE UNIQUE INDEX ix_devices_device_id ON devices(device_id);
CREATE INDEX ix_devices_id ON devices(id);

CREATE TABLE device_state_last (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR(255) NOT NULL,
    state_json TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_device_state_last_device_id UNIQUE (device_id)
);

CREATE INDEX ix_device_state_last_id ON device_state_last(id);
CREATE INDEX ix_device_state_last_updated_at ON device_state_last(updated_at);

CREATE TABLE mqtt_ack (
    id SERIAL PRIMARY KEY,
    correlation_id VARCHAR(255) NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    result VARCHAR(255) NOT NULL,
    status VARCHAR(255) NULL,
    payload_json OID NOT NULL,
    received_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NULL,
    CONSTRAINT uq_mqtt_ack_correlation_id UNIQUE (correlation_id)
);

CREATE INDEX ix_mqtt_ack_device_id ON mqtt_ack(device_id);
CREATE INDEX ix_mqtt_ack_expires_at ON mqtt_ack(expires_at);
CREATE INDEX ix_mqtt_ack_id ON mqtt_ack(id);
CREATE INDEX ix_mqtt_ack_received_at ON mqtt_ack(received_at);

CREATE TABLE plant_groups (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NULL REFERENCES users(id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL
);

CREATE TABLE plants (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NULL REFERENCES users(id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL,
    planted_at TIMESTAMP NOT NULL,
    plant_type VARCHAR(255) NULL,
    strain VARCHAR(255) NULL,
    growth_stage VARCHAR(255) NULL,
    plant_group_id INTEGER NULL REFERENCES plant_groups(id) ON DELETE SET NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL
);

CREATE TABLE plant_devices (
    id SERIAL PRIMARY KEY,
    plant_id INTEGER NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    device_id INTEGER NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    CONSTRAINT uq_plant_device_pair UNIQUE (plant_id, device_id)
);

CREATE TABLE sensor_data (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR(255) NULL,
    timestamp TIMESTAMP NULL,
    soil_moisture DOUBLE PRECISION NULL,
    air_temperature DOUBLE PRECISION NULL,
    air_humidity DOUBLE PRECISION NULL
);

CREATE INDEX ix_sensor_data_device_id ON sensor_data(device_id);
CREATE INDEX ix_sensor_data_id ON sensor_data(id);
CREATE INDEX ix_sensor_data_timestamp ON sensor_data(timestamp);

CREATE TABLE plant_journal_entries (
    id SERIAL PRIMARY KEY,
    plant_id INTEGER NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    user_id INTEGER NULL REFERENCES users(id) ON DELETE SET NULL,
    type VARCHAR(255) NOT NULL,
    text TEXT NULL,
    event_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL
);

CREATE TABLE plant_journal_photos (
    id SERIAL PRIMARY KEY,
    journal_entry_id INTEGER NOT NULL
        REFERENCES plant_journal_entries(id) ON DELETE CASCADE,
    url VARCHAR(255) NULL,
    caption VARCHAR(255) NULL,
    data BYTEA NULL,
    content_type VARCHAR(255) NULL
);

CREATE TABLE plant_journal_watering_details (
    id SERIAL PRIMARY KEY,
    journal_entry_id INTEGER NOT NULL
        REFERENCES plant_journal_entries(id) ON DELETE CASCADE,
    water_volume_l DOUBLE PRECISION NOT NULL,
    duration_s INTEGER NOT NULL,
    ph DOUBLE PRECISION NULL,
    fertilizers_per_liter TEXT NULL,
    CONSTRAINT uk_plant_journal_watering_details_entry UNIQUE (journal_entry_id)
);
