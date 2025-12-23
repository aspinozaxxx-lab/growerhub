package ru.growerhub.backend.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "sensor_data",
    indexes = {
        @Index(name = "ix_sensor_data_device_id", columnList = "device_id"),
        @Index(name = "ix_sensor_data_id", columnList = "id"),
        @Index(name = "ix_sensor_data_timestamp", columnList = "timestamp")
    }
)
public class SensorDataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "device_id", nullable = true)
    private String deviceId;

    @Column(name = "timestamp", nullable = true)
    private LocalDateTime timestamp;

    @Column(name = "soil_moisture", nullable = true)
    private Double soilMoisture;

    @Column(name = "air_temperature", nullable = true)
    private Double airTemperature;

    @Column(name = "air_humidity", nullable = true)
    private Double airHumidity;

    protected SensorDataEntity() {
    }

    public static SensorDataEntity create() {
        return new SensorDataEntity();
    }

    public Integer getId() {
        return id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Double getSoilMoisture() {
        return soilMoisture;
    }

    public void setSoilMoisture(Double soilMoisture) {
        this.soilMoisture = soilMoisture;
    }

    public Double getAirTemperature() {
        return airTemperature;
    }

    public void setAirTemperature(Double airTemperature) {
        this.airTemperature = airTemperature;
    }

    public Double getAirHumidity() {
        return airHumidity;
    }

    public void setAirHumidity(Double airHumidity) {
        this.airHumidity = airHumidity;
    }
}
