ALTER TABLE telegram_channels ADD COLUMN assistant_enabled BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE telegram_inbox (
 id BIGINT PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 address_version BIGINT NOT NULL, text TEXT, photo_id VARCHAR(255), callback_data VARCHAR(255),
 status VARCHAR(255) NOT NULL, lease_key VARCHAR(255), leased_until TIMESTAMP, created_at TIMESTAMP NOT NULL
);
CREATE INDEX telegram_inbox_pending ON telegram_inbox(status,created_at);
