package ru.growerhub.backend.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import ru.growerhub.backend.user.UserEntity;

@Entity
@Table(
    name = "devices",
    indexes = {
        @Index(name = "ix_devices_device_id", columnList = "device_id", unique = true),
        @Index(name = "ix_devices_id", columnList = "id")
    }
)
public class DeviceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "device_id", nullable = true)
    private String deviceId;

    @Column(name = "name", nullable = true)
    private String name;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = true)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private UserEntity user;

    @Column(name = "soil_moisture", nullable = true)
    private Double soilMoisture;

    @Column(name = "air_temperature", nullable = true)
    private Double airTemperature;

    @Column(name = "air_humidity", nullable = true)
    private Double airHumidity;

    @Column(name = "is_watering", nullable = true)
    private Boolean isWatering;

    @Column(name = "is_light_on", nullable = true)
    private Boolean isLightOn;

    @Column(name = "last_watering", nullable = true)
    private LocalDateTime lastWatering;

    @Column(name = "last_seen", nullable = true)
    private LocalDateTime lastSeen;

    @Column(name = "target_moisture", nullable = true)
    private Double targetMoisture;

    @Column(name = "watering_duration", nullable = true)
    private Integer wateringDuration;

    @Column(name = "watering_timeout", nullable = true)
    private Integer wateringTimeout;

    @Column(name = "watering_speed_lph", nullable = true)
    private Double wateringSpeedLph;

    @Column(name = "light_on_hour", nullable = true)
    private Integer lightOnHour;

    @Column(name = "light_off_hour", nullable = true)
    private Integer lightOffHour;

    @Column(name = "light_duration", nullable = true)
    private Integer lightDuration;

    @Column(name = "current_version", nullable = true)
    private String currentVersion;

    @Column(name = "latest_version", nullable = true)
    private String latestVersion;

    @Column(name = "update_available", nullable = true)
    private Boolean updateAvailable;

    @Column(name = "firmware_url", nullable = true)
    private String firmwareUrl;

    @OneToMany(mappedBy = "device")
    private List<PlantDeviceEntity> plantDevices;

    protected DeviceEntity() {
    }

    public Integer getId() {
        return id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getName() {
        return name;
    }

    public UserEntity getUser() {
        return user;
    }

    public Double getSoilMoisture() {
        return soilMoisture;
    }

    public Double getAirTemperature() {
        return airTemperature;
    }

    public Double getAirHumidity() {
        return airHumidity;
    }

    public Boolean getIsWatering() {
        return isWatering;
    }

    public Boolean getIsLightOn() {
        return isLightOn;
    }

    public LocalDateTime getLastWatering() {
        return lastWatering;
    }

    public LocalDateTime getLastSeen() {
        return lastSeen;
    }

    public Double getTargetMoisture() {
        return targetMoisture;
    }

    public Integer getWateringDuration() {
        return wateringDuration;
    }

    public Integer getWateringTimeout() {
        return wateringTimeout;
    }

    public Double getWateringSpeedLph() {
        return wateringSpeedLph;
    }

    public Integer getLightOnHour() {
        return lightOnHour;
    }

    public Integer getLightOffHour() {
        return lightOffHour;
    }

    public Integer getLightDuration() {
        return lightDuration;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public Boolean getUpdateAvailable() {
        return updateAvailable;
    }

    public String getFirmwareUrl() {
        return firmwareUrl;
    }

    public List<PlantDeviceEntity> getPlantDevices() {
        return plantDevices;
    }
}
