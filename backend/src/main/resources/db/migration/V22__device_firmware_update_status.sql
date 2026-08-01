ALTER TABLE devices
    ADD COLUMN firmware_hardware_profile VARCHAR(64) NULL,
    ADD COLUMN firmware_update_status VARCHAR(32) NULL,
    ADD COLUMN firmware_update_error VARCHAR(512) NULL,
    ADD COLUMN firmware_update_correlation_id VARCHAR(64) NULL,
    ADD COLUMN firmware_update_requested_at TIMESTAMP NULL,
    ADD COLUMN firmware_update_completed_at TIMESTAMP NULL;

CREATE INDEX ix_devices_firmware_update_correlation_id
    ON devices(firmware_update_correlation_id);
