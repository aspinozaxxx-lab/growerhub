ALTER TABLE automation_rooms ADD COLUMN weather_latitude DOUBLE PRECISION;
ALTER TABLE automation_rooms ADD COLUMN weather_longitude DOUBLE PRECISION;
ALTER TABLE automation_rooms ADD COLUMN weather_label VARCHAR(120);
ALTER TABLE demo_spaces ADD COLUMN weather_kind VARCHAR(20);
