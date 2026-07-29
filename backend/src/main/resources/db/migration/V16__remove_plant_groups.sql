BEGIN;

ALTER TABLE plants
    DROP COLUMN plant_group_id;

DROP TABLE plant_groups;

COMMIT;
