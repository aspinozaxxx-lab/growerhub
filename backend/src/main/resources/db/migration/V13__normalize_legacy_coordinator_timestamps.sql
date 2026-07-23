UPDATE zigbee_coordinators
SET credential_issued_at = (
        credential_issued_at AT TIME ZONE current_setting('TimeZone')
    ) AT TIME ZONE 'UTC',
    created_at = (
        created_at AT TIME ZONE current_setting('TimeZone')
    ) AT TIME ZONE 'UTC'
WHERE public_id = '00000000-0000-0000-0000-000000000001';
