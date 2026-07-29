BEGIN;

CREATE TABLE automation_farms (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ux_automation_farms_user UNIQUE (user_id)
);

INSERT INTO automation_farms(user_id, name, created_at, updated_at)
SELECT
    room.user_id,
    'Моя ферма',
    MIN(room.created_at),
    MAX(room.updated_at)
FROM automation_rooms room
GROUP BY room.user_id;

ALTER TABLE automation_rooms ADD COLUMN farm_id INTEGER NULL;

UPDATE automation_rooms room
SET farm_id = farm.id
FROM automation_farms farm
WHERE farm.user_id = room.user_id;

ALTER TABLE automation_rooms
    ADD CONSTRAINT fk_automation_room_farm
    FOREIGN KEY (farm_id) REFERENCES automation_farms(id) ON DELETE CASCADE;

ALTER TABLE automation_rooms ALTER COLUMN farm_id SET NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT box.room_id
        FROM automation_boxes box
        GROUP BY box.room_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V15 requires at most one automation box per room';
    END IF;

    IF EXISTS (
        SELECT binding.native_sensor_id
        FROM automation_resource_bindings binding
        WHERE binding.source_type = 'NATIVE_SENSOR'
          AND binding.native_sensor_id IS NOT NULL
        GROUP BY binding.native_sensor_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V15 found a native sensor assigned to multiple automation slots';
    END IF;

    IF EXISTS (
        SELECT binding.native_pump_id
        FROM automation_resource_bindings binding
        WHERE binding.source_type = 'NATIVE_PUMP'
          AND binding.native_pump_id IS NOT NULL
        GROUP BY binding.native_pump_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V15 found a native pump assigned to multiple automation slots';
    END IF;

    IF EXISTS (
        SELECT
            binding.zigbee_coordinator_id,
            LOWER(binding.zigbee_ieee_address),
            COALESCE(NULLIF(binding.zigbee_property, ''), NULLIF(binding.command_property, ''), 'state')
        FROM automation_resource_bindings binding
        WHERE binding.source_type = 'ZIGBEE_DEVICE'
          AND binding.zigbee_coordinator_id IS NOT NULL
          AND binding.zigbee_ieee_address IS NOT NULL
        GROUP BY
            binding.zigbee_coordinator_id,
            LOWER(binding.zigbee_ieee_address),
            COALESCE(NULLIF(binding.zigbee_property, ''), NULLIF(binding.command_property, ''), 'state')
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'V15 found a Zigbee property assigned to multiple automation slots';
    END IF;
END $$;

INSERT INTO automation_boxes(room_id, name, enabled, created_at, updated_at)
SELECT
    room.id,
    'Системная секция',
    room.enabled,
    room.created_at,
    room.updated_at
FROM automation_rooms room
WHERE NOT EXISTS (
    SELECT 1
    FROM automation_boxes box
    WHERE box.room_id = room.id
);

CREATE UNIQUE INDEX ux_automation_boxes_room
    ON automation_boxes(room_id);

CREATE UNIQUE INDEX ux_automation_resource_native_sensor
    ON automation_resource_bindings(native_sensor_id)
    WHERE source_type = 'NATIVE_SENSOR' AND native_sensor_id IS NOT NULL;

CREATE UNIQUE INDEX ux_automation_resource_native_pump
    ON automation_resource_bindings(native_pump_id)
    WHERE source_type = 'NATIVE_PUMP' AND native_pump_id IS NOT NULL;

CREATE UNIQUE INDEX ux_automation_resource_zigbee_property
    ON automation_resource_bindings(
        zigbee_coordinator_id,
        LOWER(zigbee_ieee_address),
        COALESCE(NULLIF(zigbee_property, ''), NULLIF(command_property, ''), 'state')
    )
    WHERE source_type = 'ZIGBEE_DEVICE'
      AND zigbee_coordinator_id IS NOT NULL
      AND zigbee_ieee_address IS NOT NULL;

CREATE INDEX ix_automation_rooms_farm
    ON automation_rooms(farm_id);

COMMIT;
