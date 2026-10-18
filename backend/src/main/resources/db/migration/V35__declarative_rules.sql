CREATE TABLE automation_rules (
 id UUID PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 revision INTEGER NOT NULL, enabled BOOLEAN NOT NULL, definition_json TEXT NOT NULL, runtime_json TEXT NOT NULL,
 created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL
);
CREATE INDEX automation_rule_owner ON automation_rules(user_id);
CREATE TABLE automation_rule_versions (
 id UUID PRIMARY KEY, rule_id UUID NOT NULL REFERENCES automation_rules(id) ON DELETE CASCADE,
 revision INTEGER NOT NULL, enabled BOOLEAN NOT NULL, definition_json TEXT NOT NULL, created_at TIMESTAMP NOT NULL,
 UNIQUE(rule_id,revision)
);
CREATE TABLE automation_rule_runs (
 id UUID PRIMARY KEY, rule_id UUID NOT NULL REFERENCES automation_rules(id) ON DELETE CASCADE,
 user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 revision INTEGER NOT NULL, trigger_key VARCHAR(255) NOT NULL, definition_json TEXT NOT NULL,
 binding_fingerprint TEXT NOT NULL, status VARCHAR(255) NOT NULL, step_index INTEGER NOT NULL,
 next_at TIMESTAMP, leased_until TIMESTAMP, lease_key VARCHAR(255), reason TEXT, result_json TEXT,
 created_at TIMESTAMP NOT NULL, UNIQUE(rule_id,trigger_key)
);
CREATE INDEX automation_rule_run_due ON automation_rule_runs(status,next_at);
CREATE INDEX automation_rule_run_history ON automation_rule_runs(rule_id,created_at);
