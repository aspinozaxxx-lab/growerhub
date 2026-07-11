BEGIN;

ALTER TABLE automation_box_plants
    ADD COLUMN IF NOT EXISTS rate_ml_per_hour INTEGER NULL;

ALTER TABLE automation_box_plants
    ADD CONSTRAINT ck_automation_box_plants_rate_positive
    CHECK (rate_ml_per_hour IS NULL OR rate_ml_per_hour > 0);

ALTER TABLE pump_plant_bindings
    ALTER COLUMN rate_ml_per_hour DROP NOT NULL;

ALTER TABLE pump_plant_bindings
    ADD CONSTRAINT ck_pump_plant_bindings_rate_positive
    CHECK (rate_ml_per_hour IS NULL OR rate_ml_per_hour > 0);

CREATE TABLE IF NOT EXISTS pump_watering_sessions (
    id BIGSERIAL PRIMARY KEY,
    pump_id INTEGER NULL REFERENCES pumps(id) ON DELETE SET NULL,
    device_id INTEGER NULL,
    device_key VARCHAR(255) NOT NULL,
    active_device_key VARCHAR(255) NULL,
    channel INTEGER NOT NULL,
    pump_label VARCHAR(255) NULL,
    user_id INTEGER NULL,
    source VARCHAR(32) NOT NULL,
    mode VARCHAR(32) NOT NULL,
    phase VARCHAR(32) NOT NULL,
    stopping_target_phase VARCHAR(32) NULL,
    planned_duration_s INTEGER NULL,
    max_active_duration_s INTEGER NULL,
    pulse_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    pulse_run_s INTEGER NULL,
    pulse_pause_s INTEGER NULL,
    active_duration_s INTEGER NOT NULL DEFAULT 0,
    journal_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    started_at TIMESTAMP NOT NULL,
    phase_started_at TIMESTAMP NOT NULL,
    last_command_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    completion_reason VARCHAR(64) NULL,
    error_message TEXT NULL,
    planned_water_volume_l DOUBLE PRECISION NULL,
    ph DOUBLE PRECISION NULL,
    fertilizers_per_liter TEXT NULL
);

ALTER TABLE pump_watering_sessions
    ADD CONSTRAINT ck_pump_watering_sessions_duration_positive
    CHECK (planned_duration_s IS NULL OR planned_duration_s > 0),
    ADD CONSTRAINT ck_pump_watering_sessions_max_duration_positive
    CHECK (max_active_duration_s IS NULL OR max_active_duration_s > 0),
    ADD CONSTRAINT ck_pump_watering_sessions_pulse_run_positive
    CHECK (pulse_run_s IS NULL OR pulse_run_s > 0),
    ADD CONSTRAINT ck_pump_watering_sessions_pulse_pause_positive
    CHECK (pulse_pause_s IS NULL OR pulse_pause_s > 0),
    ADD CONSTRAINT ck_pump_watering_sessions_active_duration_non_negative
    CHECK (active_duration_s >= 0),
    ADD CONSTRAINT ck_pump_watering_sessions_planned_volume_non_negative
    CHECK (planned_water_volume_l IS NULL OR planned_water_volume_l >= 0);

CREATE UNIQUE INDEX IF NOT EXISTS ux_pump_watering_sessions_active_device
    ON pump_watering_sessions(active_device_key)
    WHERE active_device_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_pump_watering_sessions_pump_id
    ON pump_watering_sessions(pump_id, id DESC);

CREATE INDEX IF NOT EXISTS ix_pump_watering_sessions_finished_at
    ON pump_watering_sessions(finished_at DESC);

CREATE TABLE IF NOT EXISTS pump_watering_session_boxes (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES pump_watering_sessions(id) ON DELETE CASCADE,
    box_id INTEGER NULL,
    box_name VARCHAR(255) NULL,
    room_id INTEGER NULL,
    room_name VARCHAR(255) NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_pump_watering_session_boxes_session_box
    ON pump_watering_session_boxes(session_id, box_id)
    WHERE box_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_pump_watering_session_boxes_box_session
    ON pump_watering_session_boxes(box_id, session_id DESC);

CREATE TABLE IF NOT EXISTS pump_watering_session_plants (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES pump_watering_sessions(id) ON DELETE CASCADE,
    session_box_id BIGINT NOT NULL REFERENCES pump_watering_session_boxes(id) ON DELETE CASCADE,
    plant_id INTEGER NOT NULL,
    plant_name VARCHAR(255) NULL,
    owner_id INTEGER NULL,
    rate_ml_per_hour INTEGER NULL,
    duration_s INTEGER NULL,
    water_volume_l DOUBLE PRECISION NULL
);

ALTER TABLE pump_watering_session_plants
    ADD CONSTRAINT ck_pump_watering_session_plants_rate_positive
    CHECK (rate_ml_per_hour IS NULL OR rate_ml_per_hour > 0),
    ADD CONSTRAINT ck_pump_watering_session_plants_duration_non_negative
    CHECK (duration_s IS NULL OR duration_s >= 0),
    ADD CONSTRAINT ck_pump_watering_session_plants_volume_non_negative
    CHECK (water_volume_l IS NULL OR water_volume_l >= 0);

CREATE UNIQUE INDEX IF NOT EXISTS ux_pump_watering_session_plants_target
    ON pump_watering_session_plants(session_box_id, plant_id);

CREATE INDEX IF NOT EXISTS ix_pump_watering_session_plants_session
    ON pump_watering_session_plants(session_id);

CREATE TABLE IF NOT EXISTS pump_watering_session_leaks (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES pump_watering_sessions(id) ON DELETE CASCADE,
    session_box_id BIGINT NOT NULL REFERENCES pump_watering_session_boxes(id) ON DELETE CASCADE,
    reference VARCHAR(512) NOT NULL,
    resource_binding_id INTEGER NULL,
    source_type VARCHAR(32) NULL,
    external_id VARCHAR(255) NULL,
    property VARCHAR(128) NULL,
    label VARCHAR(255) NULL,
    available_at_start BOOLEAN NULL,
    triggered_at_start BOOLEAN NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_pump_watering_session_leaks_target
    ON pump_watering_session_leaks(session_box_id, reference);

CREATE INDEX IF NOT EXISTS ix_pump_watering_session_leaks_session
    ON pump_watering_session_leaks(session_id);

ALTER TABLE plant_journal_watering_details
    ALTER COLUMN water_volume_l DROP NOT NULL;

ALTER TABLE plant_journal_watering_details
    ADD COLUMN IF NOT EXISTS pump_session_id BIGINT NULL REFERENCES pump_watering_sessions(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS plant_id INTEGER NULL,
    ADD COLUMN IF NOT EXISTS mode VARCHAR(32) NULL,
    ADD COLUMN IF NOT EXISTS completion_reason VARCHAR(64) NULL;

UPDATE plant_journal_watering_details details
SET plant_id = entries.plant_id
FROM plant_journal_entries entries
WHERE details.journal_entry_id = entries.id
  AND details.plant_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_plant_journal_watering_session_plant
    ON plant_journal_watering_details(pump_session_id, plant_id)
    WHERE pump_session_id IS NOT NULL;

WITH single_box_pumps AS (
    SELECT native_pump_id AS pump_id, MIN(scope_id) AS box_id
    FROM automation_resource_bindings
    WHERE scope_type = 'BOX'
      AND role = 'WATER_PUMP'
      AND source_type = 'NATIVE_PUMP'
      AND native_pump_id IS NOT NULL
    GROUP BY native_pump_id
    HAVING COUNT(DISTINCT scope_id) = 1
), plant_targets AS (
    SELECT pump_plants.plant_id,
           MIN(single_pump.box_id) AS box_id,
           MAX(pump_plants.rate_ml_per_hour) AS rate_ml_per_hour
    FROM single_box_pumps single_pump
    JOIN pump_plant_bindings pump_plants ON pump_plants.pump_id = single_pump.pump_id
    GROUP BY pump_plants.plant_id
    HAVING COUNT(DISTINCT single_pump.box_id) = 1
)
UPDATE automation_box_plants box_plants
SET box_id = plant_targets.box_id,
    rate_ml_per_hour = plant_targets.rate_ml_per_hour
FROM plant_targets
WHERE box_plants.plant_id = plant_targets.plant_id;

WITH single_box_pumps AS (
    SELECT native_pump_id AS pump_id, MIN(scope_id) AS box_id
    FROM automation_resource_bindings
    WHERE scope_type = 'BOX'
      AND role = 'WATER_PUMP'
      AND source_type = 'NATIVE_PUMP'
      AND native_pump_id IS NOT NULL
    GROUP BY native_pump_id
    HAVING COUNT(DISTINCT scope_id) = 1
), plant_targets AS (
    SELECT pump_plants.plant_id,
           MIN(single_pump.box_id) AS box_id,
           MAX(pump_plants.rate_ml_per_hour) AS rate_ml_per_hour
    FROM single_box_pumps single_pump
    JOIN pump_plant_bindings pump_plants ON pump_plants.pump_id = single_pump.pump_id
    GROUP BY pump_plants.plant_id
    HAVING COUNT(DISTINCT single_pump.box_id) = 1
)
INSERT INTO automation_box_plants(box_id, plant_id, rate_ml_per_hour, created_at)
SELECT plant_targets.box_id, plant_targets.plant_id, plant_targets.rate_ml_per_hour, NOW()
FROM plant_targets
ON CONFLICT DO NOTHING;

COMMIT;
