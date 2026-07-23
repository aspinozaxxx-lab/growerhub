ALTER TABLE devices ADD COLUMN device_token_hash VARCHAR(64) NULL;
ALTER TABLE devices ADD COLUMN device_token_issued_at TIMESTAMP NULL;
