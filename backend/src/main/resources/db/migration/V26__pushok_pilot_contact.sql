ALTER TABLE users
    ADD COLUMN pushok_pilot_contact_method VARCHAR(16),
    ADD COLUMN pushok_pilot_contact VARCHAR(254),
    ADD COLUMN pushok_pilot_equipment VARCHAR(500),
    ADD COLUMN pushok_pilot_requested_at TIMESTAMP,
    ADD COLUMN pushok_pilot_contacted_at TIMESTAMP;

ALTER TABLE users ADD CONSTRAINT ck_users_pushok_pilot CHECK (
    (pushok_pilot_requested_at IS NULL AND pushok_pilot_contact_method IS NULL
        AND pushok_pilot_contact IS NULL AND pushok_pilot_equipment IS NULL AND pushok_pilot_contacted_at IS NULL)
    OR (account_kind = 'ACCOUNT' AND pushok_pilot_requested_at IS NOT NULL
        AND pushok_pilot_contact_method IS NOT NULL AND pushok_pilot_contact_method IN ('TELEGRAM', 'EMAIL', 'OTHER')
        AND pushok_pilot_contact IS NOT NULL AND length(trim(pushok_pilot_contact)) > 0)
);
