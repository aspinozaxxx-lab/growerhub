-- Translitem: sushhestvujushhie akkaunty i ustrojstva sohranjajut fizicheskij rezhim.
ALTER TABLE users ADD COLUMN account_kind VARCHAR(16) NOT NULL DEFAULT 'ACCOUNT';
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ADD CONSTRAINT ck_users_account_kind CHECK (
    (account_kind = 'ACCOUNT' AND email IS NOT NULL AND role <> 'demo')
    OR (account_kind = 'DEMO' AND email IS NULL AND role = 'demo')
);
ALTER TABLE devices ADD COLUMN execution_kind VARCHAR(16) NOT NULL DEFAULT 'PHYSICAL';
ALTER TABLE devices ADD CONSTRAINT ck_devices_execution_kind CHECK (execution_kind IN ('PHYSICAL', 'SIMULATED'));
ALTER TABLE zigbee_coordinators ADD COLUMN execution_kind VARCHAR(16) NOT NULL DEFAULT 'PHYSICAL';
ALTER TABLE zigbee_coordinators ADD CONSTRAINT ck_zigbee_execution_kind CHECK (execution_kind IN ('PHYSICAL', 'SIMULATED'));

CREATE TABLE demo_spaces (
    id UUID PRIMARY KEY,
    data_user_id INTEGER NOT NULL UNIQUE REFERENCES users(id),
    account_user_id INTEGER UNIQUE REFERENCES users(id) ON DELETE SET NULL,
    generation INTEGER NOT NULL,
    template_version INTEGER NOT NULL,
    locale VARCHAR(8) NOT NULL,
    admission_key VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_active_at TIMESTAMP NOT NULL,
    last_tick_at TIMESTAMP,
    action_window_started_at TIMESTAMP,
    actions_in_window INTEGER NOT NULL DEFAULT 0,
    reset_window_started_at TIMESTAMP,
    resets_in_window INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMP,
    paused BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX ix_demo_spaces_activity ON demo_spaces(last_active_at);
CREATE INDEX ix_demo_spaces_expiry ON demo_spaces(expires_at);
CREATE INDEX ix_demo_spaces_admission ON demo_spaces(admission_key, created_at);

CREATE TABLE auth_demo_sessions (
    id UUID PRIMARY KEY,
    space_id UUID NOT NULL REFERENCES demo_spaces(id) ON DELETE CASCADE,
    generation INTEGER NOT NULL,
    account_user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    refresh_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL
);
CREATE INDEX ix_auth_demo_sessions_expiry ON auth_demo_sessions(expires_at);

CREATE TABLE demo_devices (
    id UUID PRIMARY KEY,
    space_id UUID NOT NULL REFERENCES demo_spaces(id) ON DELETE CASCADE,
    profile_key VARCHAR(32) NOT NULL,
    target_id VARCHAR(128) NOT NULL UNIQUE,
    native_device_id INTEGER,
    coordinator_id INTEGER,
    state_json TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    stop_at TIMESTAMP
);
CREATE INDEX ix_demo_devices_space ON demo_devices(space_id);

CREATE TABLE demo_capacity (id INTEGER PRIMARY KEY CHECK (id = 1));
INSERT INTO demo_capacity (id) VALUES (1);
