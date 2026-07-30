ALTER TABLE users
    ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'Europe/Moscow',
    ADD COLUMN onboarding_completed_at TIMESTAMP NULL;

UPDATE users u
SET onboarding_completed_at = CURRENT_TIMESTAMP
WHERE EXISTS (
    SELECT 1
    FROM zigbee_coordinators zc
    WHERE zc.user_id = u.id
      AND (zc.first_device_seen_at IS NOT NULL OR EXISTS (
          SELECT 1
          FROM zigbee_device_snapshots zds
          WHERE zds.coordinator_id = zc.id
            AND zds.coordinator = FALSE
      ))
)
AND EXISTS (
    SELECT 1
    FROM automation_rooms ar
    JOIN automation_boxes ab ON ab.room_id = ar.id
    WHERE ar.user_id = u.id
);
