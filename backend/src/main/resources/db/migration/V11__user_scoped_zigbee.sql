BEGIN;

CREATE TABLE zigbee_coordinators (
    id SERIAL PRIMARY KEY,
    public_id UUID NOT NULL,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    mqtt_username VARCHAR(96) NOT NULL,
    base_topic VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    archived_at TIMESTAMP NULL,
    last_seen_at TIMESTAMP NULL,
    connected_at TIMESTAMP NULL,
    first_device_seen_at TIMESTAMP NULL,
    credential_issued_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ux_zigbee_coordinators_public_id UNIQUE (public_id),
    CONSTRAINT ux_zigbee_coordinators_mqtt_username UNIQUE (mqtt_username),
    CONSTRAINT ux_zigbee_coordinators_base_topic UNIQUE (base_topic)
);

CREATE INDEX ix_zigbee_coordinators_user_status
    ON zigbee_coordinators(user_id, status);

ALTER TABLE automation_rooms ADD COLUMN user_id INTEGER NULL;
ALTER TABLE automation_resource_bindings ADD COLUMN zigbee_coordinator_id INTEGER NULL;

DO $$
DECLARE
    has_legacy_data BOOLEAN;
    owner_id INTEGER;
    legacy_coordinator_id INTEGER;
BEGIN
    has_legacy_data := EXISTS (SELECT 1 FROM automation_rooms)
        OR EXISTS (SELECT 1 FROM automation_resource_bindings)
        OR EXISTS (SELECT 1 FROM zigbee_bridge_snapshots)
        OR EXISTS (SELECT 1 FROM zigbee_device_snapshots)
        OR EXISTS (SELECT 1 FROM zigbee_command_response_snapshots)
        OR EXISTS (SELECT 1 FROM zigbee_device_state_events)
        OR EXISTS (SELECT 1 FROM zigbee_device_property_readings);

    IF has_legacy_data THEN
        SELECT id
        INTO owner_id
        FROM users
        WHERE LOWER(email) = LOWER(NULLIF('${legacyOwnerEmail}', ''))
        ORDER BY id
        LIMIT 1;

        IF owner_id IS NULL THEN
            RAISE EXCEPTION 'SELF_SERVICE_LEGACY_OWNER_EMAIL must identify an existing user before V11 migration';
        END IF;

        INSERT INTO zigbee_coordinators (
            public_id,
            user_id,
            name,
            mqtt_username,
            base_topic,
            status,
            credential_issued_at,
            created_at,
            updated_at
        ) VALUES (
            '00000000-0000-0000-0000-000000000001',
            owner_id,
            'Legacy coordinator',
            'legacy',
            'zigbee2growerhub',
            'OFFLINE',
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        ) RETURNING id INTO legacy_coordinator_id;

        IF legacy_coordinator_id <> 1 THEN
            RAISE EXCEPTION 'Legacy coordinator must be the first coordinator in a new V11 table';
        END IF;

        UPDATE automation_rooms SET user_id = owner_id WHERE user_id IS NULL;
        UPDATE automation_resource_bindings
        SET zigbee_coordinator_id = legacy_coordinator_id
        WHERE source_type = 'ZIGBEE_DEVICE' AND zigbee_coordinator_id IS NULL;
    END IF;
END $$;

ALTER TABLE zigbee_bridge_snapshots ADD COLUMN coordinator_id INTEGER NOT NULL DEFAULT 1;
ALTER TABLE zigbee_device_snapshots ADD COLUMN coordinator_id INTEGER NOT NULL DEFAULT 1;
ALTER TABLE zigbee_command_response_snapshots ADD COLUMN coordinator_id INTEGER NOT NULL DEFAULT 1;
ALTER TABLE zigbee_device_state_events ADD COLUMN coordinator_id INTEGER NOT NULL DEFAULT 1;
ALTER TABLE zigbee_device_property_readings ADD COLUMN coordinator_id INTEGER NOT NULL DEFAULT 1;

ALTER TABLE zigbee_bridge_snapshots ALTER COLUMN coordinator_id DROP DEFAULT;
ALTER TABLE zigbee_device_snapshots ALTER COLUMN coordinator_id DROP DEFAULT;
ALTER TABLE zigbee_command_response_snapshots ALTER COLUMN coordinator_id DROP DEFAULT;
ALTER TABLE zigbee_device_state_events ALTER COLUMN coordinator_id DROP DEFAULT;
ALTER TABLE zigbee_device_property_readings ALTER COLUMN coordinator_id DROP DEFAULT;

ALTER TABLE zigbee_bridge_snapshots
    ADD CONSTRAINT fk_zigbee_bridge_coordinator
    FOREIGN KEY (coordinator_id) REFERENCES zigbee_coordinators(id) ON DELETE CASCADE;
ALTER TABLE zigbee_device_snapshots
    ADD CONSTRAINT fk_zigbee_device_coordinator
    FOREIGN KEY (coordinator_id) REFERENCES zigbee_coordinators(id) ON DELETE CASCADE;
ALTER TABLE zigbee_command_response_snapshots
    ADD CONSTRAINT fk_zigbee_command_response_coordinator
    FOREIGN KEY (coordinator_id) REFERENCES zigbee_coordinators(id) ON DELETE CASCADE;
ALTER TABLE zigbee_device_state_events
    ADD CONSTRAINT fk_zigbee_state_event_coordinator
    FOREIGN KEY (coordinator_id) REFERENCES zigbee_coordinators(id) ON DELETE CASCADE;
ALTER TABLE zigbee_device_property_readings
    ADD CONSTRAINT fk_zigbee_property_reading_coordinator
    FOREIGN KEY (coordinator_id) REFERENCES zigbee_coordinators(id) ON DELETE CASCADE;
ALTER TABLE automation_rooms
    ADD CONSTRAINT fk_automation_room_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE automation_resource_bindings
    ADD CONSTRAINT fk_automation_resource_zigbee_coordinator
    FOREIGN KEY (zigbee_coordinator_id) REFERENCES zigbee_coordinators(id) ON DELETE CASCADE;

ALTER TABLE automation_rooms ALTER COLUMN user_id SET NOT NULL;

DROP INDEX IF EXISTS ux_zigbee_device_snapshots_ieee;
DROP INDEX IF EXISTS ix_zigbee_device_snapshots_friendly;
DROP INDEX IF EXISTS ix_zigbee_state_events_ieee_ts;
DROP INDEX IF EXISTS ix_zigbee_state_events_friendly_ts;
DROP INDEX IF EXISTS ix_zigbee_property_readings_ieee_property_ts;
DROP INDEX IF EXISTS ix_zigbee_property_readings_friendly_property_ts;

CREATE UNIQUE INDEX ux_zigbee_bridge_snapshots_coordinator
    ON zigbee_bridge_snapshots(coordinator_id);
CREATE UNIQUE INDEX ux_zigbee_command_response_coordinator
    ON zigbee_command_response_snapshots(coordinator_id);
CREATE UNIQUE INDEX ux_zigbee_device_snapshots_coordinator_ieee
    ON zigbee_device_snapshots(coordinator_id, ieee_address)
    WHERE ieee_address IS NOT NULL;
CREATE UNIQUE INDEX ux_zigbee_device_snapshots_coordinator_friendly
    ON zigbee_device_snapshots(coordinator_id, friendly_name);
CREATE INDEX ix_zigbee_state_events_coordinator_ieee_ts
    ON zigbee_device_state_events(coordinator_id, ieee_address, ts);
CREATE INDEX ix_zigbee_state_events_coordinator_friendly_ts
    ON zigbee_device_state_events(coordinator_id, friendly_name, ts);
CREATE INDEX ix_zigbee_property_readings_coordinator_ieee_property_ts
    ON zigbee_device_property_readings(coordinator_id, ieee_address, property, ts);
CREATE INDEX ix_zigbee_property_readings_coordinator_friendly_property_ts
    ON zigbee_device_property_readings(coordinator_id, friendly_name, property, ts);
CREATE INDEX ix_automation_rooms_user
    ON automation_rooms(user_id);
CREATE INDEX ix_automation_resource_zigbee_coordinator
    ON automation_resource_bindings(zigbee_coordinator_id);

COMMIT;
