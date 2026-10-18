CREATE TABLE assistant_feature_requests (
    id UUID PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    benefit TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    note TEXT NOT NULL DEFAULT '',
    revision INTEGER NOT NULL DEFAULT 1,
    notification_queued BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT assistant_request_status CHECK (status IN ('new', 'planned', 'declined', 'done'))
);
CREATE INDEX assistant_requests_owner_date ON assistant_feature_requests(user_id, created_at DESC);
CREATE INDEX assistant_requests_pending_notice ON assistant_feature_requests(notification_queued, created_at);
