CREATE TABLE assistant_guard (
 id INTEGER PRIMARY KEY, public_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 rejected BIGINT NOT NULL DEFAULT 0, alert_key VARCHAR(255), denials_json TEXT NOT NULL DEFAULT '{}'
);
INSERT INTO assistant_guard(id) VALUES (1);
CREATE TABLE assistant_conversations (
 id UUID PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 title VARCHAR(255), model VARCHAR(255), effort VARCHAR(255),
 created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL
);
CREATE INDEX assistant_conversation_owner ON assistant_conversations(user_id,updated_at DESC);
CREATE TABLE assistant_jobs (
 id UUID PRIMARY KEY, conversation_id UUID NOT NULL REFERENCES assistant_conversations(id) ON DELETE CASCADE,
 user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 client_key UUID NOT NULL, request_hash VARCHAR(255), billing_key VARCHAR(255), ip_key VARCHAR(255), visitor_key VARCHAR(255),
 channel VARCHAR(255), guest BOOLEAN NOT NULL, provider VARCHAR(255), model VARCHAR(255), effort VARCHAR(255),
 status VARCHAR(255), input_text TEXT, output_text TEXT, attachments_json TEXT, tool_results_json TEXT,
 error_code VARCHAR(255), lease_key VARCHAR(255), leased_until TIMESTAMP,
 created_at TIMESTAMP NOT NULL, finished_at TIMESTAMP,
 tool_calls INTEGER NOT NULL DEFAULT 0, dispatched BOOLEAN NOT NULL DEFAULT FALSE,
 telegram_queued BOOLEAN NOT NULL DEFAULT FALSE, report_summary TEXT,
 UNIQUE(user_id,client_key)
);
CREATE INDEX assistant_job_queue ON assistant_jobs(status,created_at);
CREATE INDEX assistant_job_conversation ON assistant_jobs(conversation_id,created_at);
CREATE TABLE assistant_usage (
 id UUID PRIMARY KEY, billing_key VARCHAR(255), ip_key VARCHAR(255), visitor_key VARCHAR(255),
 guest BOOLEAN NOT NULL, paid BOOLEAN NOT NULL, status VARCHAR(255),
 reserved_micros BIGINT NOT NULL DEFAULT 0, charged_micros BIGINT NOT NULL DEFAULT 0,
 input_tokens BIGINT NOT NULL DEFAULT 0, output_tokens BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL, finished_at TIMESTAMP
);
CREATE INDEX assistant_usage_window ON assistant_usage(created_at);
CREATE INDEX assistant_usage_identity ON assistant_usage(billing_key,created_at);
CREATE TABLE assistant_photos (
 id UUID PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 conversation_id UUID NOT NULL REFERENCES assistant_conversations(id) ON DELETE CASCADE,
 billing_key VARCHAR(255), ip_key VARCHAR(255), status VARCHAR(255), size BIGINT NOT NULL,
 bytes BYTEA, created_at TIMESTAMP NOT NULL
);
CREATE INDEX assistant_photo_owner ON assistant_photos(user_id,created_at);
CREATE TABLE assistant_proposals (
 id UUID PRIMARY KEY, job_id UUID NOT NULL REFERENCES assistant_jobs(id) ON DELETE CASCADE,
 tool_call_id VARCHAR(255), user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 kind VARCHAR(255), title VARCHAR(255), payload_json TEXT, expected_hash VARCHAR(255),
 revision VARCHAR(255), status VARCHAR(255), telegram_queued BOOLEAN NOT NULL DEFAULT FALSE, result_json TEXT, expires_at TIMESTAMP NOT NULL,
 created_at TIMESTAMP NOT NULL, applying_at TIMESTAMP, UNIQUE(job_id,tool_call_id)
);
CREATE TABLE assistant_preferences (
 user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, telegram BOOLEAN NOT NULL DEFAULT FALSE,
 report VARCHAR(255) NOT NULL DEFAULT 'off', report_hour INTEGER NOT NULL DEFAULT 9, weekday INTEGER NOT NULL DEFAULT 1,
 conversation_id UUID REFERENCES assistant_conversations(id) ON DELETE SET NULL, last_report_key VARCHAR(255), updated_at TIMESTAMP
);
