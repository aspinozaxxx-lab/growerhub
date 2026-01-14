CREATE TABLE advisor_watering_advice (
    id SERIAL PRIMARY KEY,
    plant_id INTEGER NOT NULL,
    is_due BOOLEAN NOT NULL,
    recommended_water_volume_l DOUBLE PRECISION NULL,
    recommended_ph DOUBLE PRECISION NULL,
    recommended_fertilizers_per_liter TEXT NULL,
    created_at TIMESTAMP NOT NULL,
    valid_until TIMESTAMP NOT NULL,
    CONSTRAINT uq_advisor_watering_advice_plant UNIQUE (plant_id)
);
