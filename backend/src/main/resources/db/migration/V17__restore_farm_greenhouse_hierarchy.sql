BEGIN;

CREATE TEMPORARY TABLE v17_farm_rooms (
    legacy_farm_id INTEGER PRIMARY KEY,
    room_id INTEGER NOT NULL
) ON COMMIT DROP;

WITH created AS (
    INSERT INTO automation_rooms(user_id, farm_id, name, enabled, created_at, updated_at)
    SELECT
        farm.user_id,
        farm.id,
        farm.name,
        TRUE,
        farm.created_at,
        farm.updated_at
    FROM automation_farms farm
    RETURNING id, farm_id
)
INSERT INTO v17_farm_rooms(legacy_farm_id, room_id)
SELECT farm_id, id
FROM created;

CREATE TEMPORARY TABLE v17_greenhouses (
    legacy_room_id INTEGER PRIMARY KEY,
    box_id INTEGER NOT NULL,
    parent_room_id INTEGER NOT NULL
) ON COMMIT DROP;

INSERT INTO v17_greenhouses(legacy_room_id, box_id, parent_room_id)
SELECT
    room.id,
    box.id,
    mapped.room_id
FROM automation_rooms room
JOIN automation_boxes box ON box.room_id = room.id
JOIN v17_farm_rooms mapped ON mapped.legacy_farm_id = room.farm_id
WHERE room.id <> mapped.room_id;

DROP INDEX ux_automation_boxes_room;

UPDATE automation_boxes box
SET
    room_id = mapped.parent_room_id,
    name = room.name,
    enabled = room.enabled,
    updated_at = GREATEST(box.updated_at, room.updated_at)
FROM v17_greenhouses mapped
JOIN automation_rooms room ON room.id = mapped.legacy_room_id
WHERE box.id = mapped.box_id;

UPDATE automation_resource_bindings binding
SET
    scope_type = 'BOX',
    scope_id = mapped.box_id,
    updated_at = CURRENT_TIMESTAMP
FROM v17_greenhouses mapped
WHERE binding.scope_type = 'ROOM'
  AND binding.scope_id = mapped.legacy_room_id;

UPDATE automation_scenario_configs box_config
SET
    config_json = (
        COALESCE(NULLIF(box_config.config_json, '')::jsonb, '{}'::jsonb)
        || jsonb_strip_nulls(jsonb_build_object(
            'off_delay_minutes', NULLIF(room_config.config_json, '')::jsonb -> 'off_delay_minutes',
            'min_toggle_minutes', NULLIF(room_config.config_json, '')::jsonb -> 'min_toggle_minutes'
        ))
    )::text,
    updated_at = GREATEST(box_config.updated_at, room_config.updated_at)
FROM v17_greenhouses mapped
JOIN automation_scenario_configs room_config
  ON room_config.scope_type = 'ROOM'
 AND room_config.scope_id = mapped.legacy_room_id
 AND room_config.scenario_type = 'ROOM_CLIMATE'
WHERE box_config.scope_type = 'BOX'
  AND box_config.scope_id = mapped.box_id
  AND box_config.scenario_type = 'BOX_CLIMATE';

INSERT INTO automation_scenario_configs(
    scope_type,
    scope_id,
    scenario_type,
    enabled,
    config_json,
    created_at,
    updated_at
)
SELECT
    'ROOM',
    mapped.room_id,
    'ROOM_CLIMATE',
    COALESCE(BOOL_OR(box_config.enabled), FALSE),
    COALESCE(
        (
            SELECT jsonb_strip_nulls(jsonb_build_object(
                'off_delay_minutes', NULLIF(source.config_json, '')::jsonb -> 'off_delay_minutes',
                'min_toggle_minutes', NULLIF(source.config_json, '')::jsonb -> 'min_toggle_minutes'
            ))::text
            FROM automation_rooms legacy_room
            JOIN automation_scenario_configs source
              ON source.scope_type = 'ROOM'
             AND source.scope_id = legacy_room.id
             AND source.scenario_type = 'ROOM_CLIMATE'
            WHERE legacy_room.farm_id = mapped.legacy_farm_id
            ORDER BY source.id
            LIMIT 1
        ),
        '{"off_delay_minutes":5,"min_toggle_minutes":5}'
    ),
    MIN(COALESCE(box_config.created_at, parent_room.created_at)),
    MAX(COALESCE(box_config.updated_at, parent_room.updated_at))
FROM v17_farm_rooms mapped
JOIN automation_rooms parent_room ON parent_room.id = mapped.room_id
LEFT JOIN automation_boxes box ON box.room_id = mapped.room_id
LEFT JOIN automation_scenario_configs box_config
  ON box_config.scope_type = 'BOX'
 AND box_config.scope_id = box.id
 AND box_config.scenario_type = 'BOX_CLIMATE'
GROUP BY mapped.legacy_farm_id, mapped.room_id
ON CONFLICT (scope_type, scope_id, scenario_type) DO NOTHING;

UPDATE automation_action_log action
SET
    scope_type = 'BOX',
    scope_id = mapped.box_id,
    scenario_type = CASE
        WHEN action.scenario_type = 'ROOM_CLIMATE' THEN 'BOX_CLIMATE'
        ELSE action.scenario_type
    END
FROM v17_greenhouses mapped
WHERE action.scope_type = 'ROOM'
  AND action.scope_id = mapped.legacy_room_id;

DELETE FROM automation_scenario_states state
USING v17_greenhouses mapped
WHERE state.scope_type = 'ROOM'
  AND state.scope_id = mapped.legacy_room_id;

DELETE FROM automation_scenario_configs config
USING v17_greenhouses mapped
WHERE config.scope_type = 'ROOM'
  AND config.scope_id = mapped.legacy_room_id;

DELETE FROM automation_rooms room
USING v17_greenhouses mapped
WHERE room.id = mapped.legacy_room_id;

DROP INDEX ix_automation_rooms_farm;

ALTER TABLE automation_rooms DROP CONSTRAINT fk_automation_room_farm;
ALTER TABLE automation_rooms DROP COLUMN farm_id;

DROP TABLE automation_farms;

COMMIT;
