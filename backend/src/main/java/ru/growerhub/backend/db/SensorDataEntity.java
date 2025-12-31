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

    // Vlazhnost' pochvy po portu 1.
    @Column(name = "soil_moisture_1", nullable = true)
    private Double soilMoisture1;

    // Vlazhnost' pochvy po portu 2.
    @Column(name = "soil_moisture_2", nullable = true)
    private Double soilMoisture2;

    @Column(name = "air_temperature", nullable = true)
    private Double airTemperature;

    @Column(name = "air_humidity", nullable = true)
    private Double airHumidity;

    // Status rele nasosa iz state.
    @Column(name = "pump_relay_on", nullable = true)
    private Boolean pumpRelayOn;

    // Status rele sveta iz state.
    @Column(name = "light_relay_on", nullable = true)
    private Boolean lightRelayOn;

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

    // Vozvrashaet vlazhnost' pochvy dlya porta 1.
    public Double getSoilMoisture1() {
        return soilMoisture1;
    }

    // Ustanavlivaet vlazhnost' pochvy dlya porta 1.
    public void setSoilMoisture1(Double soilMoisture1) {
        this.soilMoisture1 = soilMoisture1;
    }

    // Vozvrashaet vlazhnost' pochvy dlya porta 2.
    public Double getSoilMoisture2() {
        return soilMoisture2;
    }

    // Ustanavlivaet vlazhnost' pochvy dlya porta 2.
    public void setSoilMoisture2(Double soilMoisture2) {
        this.soilMoisture2 = soilMoisture2;
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

    // Vozvrashaet status rele nasosa.
    public Boolean getPumpRelayOn() {
        return pumpRelayOn;
    }

    // Ustanavlivaet status rele nasosa.
    public void setPumpRelayOn(Boolean pumpRelayOn) {
        this.pumpRelayOn = pumpRelayOn;
    }

    // Vozvrashaet status rele sveta.
    public Boolean getLightRelayOn() {
        return lightRelayOn;
    }

    // Ustanavlivaet status rele sveta.
    public void setLightRelayOn(Boolean lightRelayOn) {
        this.lightRelayOn = lightRelayOn;
    }
}
