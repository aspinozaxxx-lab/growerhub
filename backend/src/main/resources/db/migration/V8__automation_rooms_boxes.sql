BEGIN;

CREATE TABLE IF NOT EXISTS automation_rooms (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS automation_boxes (
    id SERIAL PRIMARY KEY,
    room_id INTEGER NOT NULL REFERENCES automation_rooms(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_automation_boxes_room
    ON automation_boxes(room_id);

CREATE TABLE IF NOT EXISTS automation_box_plants (
    id SERIAL PRIMARY KEY,
    box_id INTEGER NOT NULL REFERENCES automation_boxes(id) ON DELETE CASCADE,
    plant_id INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_automation_box_plants_box_plant
    ON automation_box_plants(box_id, plant_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_automation_box_plants_plant
    ON automation_box_plants(plant_id);

CREATE TABLE IF NOT EXISTS automation_resource_bindings (
    id SERIAL PRIMARY KEY,
    scope_type VARCHAR(16) NOT NULL,
    scope_id INTEGER NOT NULL,
    role VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    native_sensor_id INTEGER NULL,
    native_pump_id INTEGER NULL,
    zigbee_ieee_address VARCHAR(64) NULL,
    zigbee_property VARCHAR(128) NULL,
    command_property VARCHAR(128) NULL,
    on_value VARCHAR(128) NULL,
    off_value VARCHAR(128) NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_automation_resource_bindings_scope_role
    ON automation_resource_bindings(scope_type, scope_id, role);

CREATE INDEX IF NOT EXISTS ix_automation_resource_bindings_zigbee
    ON automation_resource_bindings(zigbee_ieee_address);

CREATE TABLE IF NOT EXISTS automation_scenario_configs (
    id SERIAL PRIMARY KEY,
    scope_type VARCHAR(16) NOT NULL,
    scope_id INTEGER NOT NULL,
    scenario_type VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    config_json TEXT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_automation_scenario_configs_scope_type
    ON automation_scenario_configs(scope_type, scope_id, scenario_type);

CREATE TABLE IF NOT EXISTS automation_scenario_states (
    id SERIAL PRIMARY KEY,
    scope_type VARCHAR(16) NOT NULL,
    scope_id INTEGER NOT NULL,
    scenario_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    unavailable_reason TEXT NULL,
    last_evaluated_at TIMESTAMP NULL,
    last_action_at TIMESTAMP NULL,
    ac_request_active BOOLEAN NOT NULL DEFAULT FALSE,
    manual_pause_until TIMESTAMP NULL,
    runtime_json TEXT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_automation_scenario_states_scope_type
    ON automation_scenario_states(scope_type, scope_id, scenario_type);

CREATE TABLE IF NOT EXISTS automation_action_log (
    id SERIAL PRIMARY KEY,
    scope_type VARCHAR(16) NOT NULL,
    scope_id INTEGER NOT NULL,
    scenario_type VARCHAR(64) NOT NULL,
    resource_binding_id INTEGER NULL REFERENCES automation_resource_bindings(id) ON DELETE SET NULL,
    action VARCHAR(128) NOT NULL,
    reason TEXT NULL,
    result VARCHAR(32) NOT NULL,
    duration_s INTEGER NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_automation_action_log_scope_created
    ON automation_action_log(scope_type, scope_id, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_automation_action_log_scenario_created
    ON automation_action_log(scenario_type, created_at DESC);

COMMIT;
