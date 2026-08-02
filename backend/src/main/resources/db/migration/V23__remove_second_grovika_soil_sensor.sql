BEGIN;

DELETE FROM automation_resource_bindings
WHERE native_sensor_id IN (
    SELECT sensor.id
    FROM sensors sensor
    JOIN devices device ON device.id = sensor.device_id
    WHERE device.device_id ~* '^GROVIKA_[0-9A-F]{6}$'
      AND sensor.type = 'SOIL_MOISTURE'
      AND sensor.channel <> 0
);

DELETE FROM sensors sensor
USING devices device
WHERE device.id = sensor.device_id
  AND device.device_id ~* '^GROVIKA_[0-9A-F]{6}$'
  AND sensor.type = 'SOIL_MOISTURE'
  AND sensor.channel <> 0;

COMMIT;
