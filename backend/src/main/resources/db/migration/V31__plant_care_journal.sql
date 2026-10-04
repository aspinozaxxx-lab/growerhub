ALTER TABLE plants ALTER COLUMN planted_at DROP NOT NULL;
ALTER TABLE plants ADD COLUMN description TEXT;
ALTER TABLE plants ADD COLUMN location_label VARCHAR(255);
ALTER TABLE plant_journal_entries ADD COLUMN care_action VARCHAR(32);
ALTER TABLE plant_journal_entries ADD COLUMN client_key VARCHAR(128);
CREATE UNIQUE INDEX ux_journal_client_key ON plant_journal_entries(user_id, client_key);
CREATE INDEX ix_journal_owner_event ON plant_journal_entries(user_id, event_at DESC, id DESC);
ALTER TABLE plant_journal_photos ADD COLUMN content_hash VARCHAR(64);
CREATE UNIQUE INDEX ux_journal_photo_hash ON plant_journal_photos(journal_entry_id, content_hash);

CREATE TABLE plant_care_reminders (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plant_id INTEGER NOT NULL REFERENCES plants(id) ON DELETE CASCADE,
    action VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    due_at TIMESTAMP NOT NULL,
    repeat_days INTEGER,
    repeat_mode VARCHAR(32) NOT NULL,
    occurrence_key VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX ix_care_reminder_due ON plant_care_reminders(user_id, enabled, due_at);

ALTER TABLE users ADD COLUMN care_started_at TIMESTAMP;
