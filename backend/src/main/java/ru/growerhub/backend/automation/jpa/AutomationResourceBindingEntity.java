package ru.growerhub.backend.automation.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "automation_resource_bindings")
public class AutomationResourceBindingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "scope_type", nullable = false)
    private String scopeType;

    @Column(name = "scope_id", nullable = false)
    private Integer scopeId;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "native_sensor_id")
    private Integer nativeSensorId;

    @Column(name = "native_pump_id")
    private Integer nativePumpId;

    @Column(name = "zigbee_ieee_address")
    private String zigbeeIeeeAddress;

    @Column(name = "zigbee_property")
    private String zigbeeProperty;

    @Column(name = "command_property")
    private String commandProperty;

    @Column(name = "on_value")
    private String onValue;

    @Column(name = "off_value")
    private String offValue;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AutomationResourceBindingEntity() {
    }

    public static AutomationResourceBindingEntity create(
            String scopeType,
            Integer scopeId,
            String role,
            LocalDateTime now
    ) {
        AutomationResourceBindingEntity entity = new AutomationResourceBindingEntity();
        entity.scopeType = scopeType;
        entity.scopeId = scopeId;
        entity.role = role;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public Integer getId() {
        return id;
    }

    public String getScopeType() {
        return scopeType;
    }

    public Integer getScopeId() {
        return scopeId;
    }

    public String getRole() {
        return role;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public Integer getNativeSensorId() {
        return nativeSensorId;
    }

    public void setNativeSensorId(Integer nativeSensorId) {
        this.nativeSensorId = nativeSensorId;
    }

    public Integer getNativePumpId() {
        return nativePumpId;
    }

    public void setNativePumpId(Integer nativePumpId) {
        this.nativePumpId = nativePumpId;
    }

    public String getZigbeeIeeeAddress() {
        return zigbeeIeeeAddress;
    }

    public void setZigbeeIeeeAddress(String zigbeeIeeeAddress) {
        this.zigbeeIeeeAddress = zigbeeIeeeAddress;
    }

    public String getZigbeeProperty() {
        return zigbeeProperty;
    }

    public void setZigbeeProperty(String zigbeeProperty) {
        this.zigbeeProperty = zigbeeProperty;
    }

    public String getCommandProperty() {
        return commandProperty;
    }

    public void setCommandProperty(String commandProperty) {
        this.commandProperty = commandProperty;
    }

    public String getOnValue() {
        return onValue;
    }

    public void setOnValue(String onValue) {
        this.onValue = onValue;
    }

    public String getOffValue() {
        return offValue;
    }

    public void setOffValue(String offValue) {
        this.offValue = offValue;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
