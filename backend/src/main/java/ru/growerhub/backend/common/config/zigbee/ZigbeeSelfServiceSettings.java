package ru.growerhub.backend.common.config.zigbee;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zigbee.self-service")
public class ZigbeeSelfServiceSettings {
    private boolean enabled;
    private String mqttServer = "mqtts://growerhub.ru:8883";
    private String brokerRole = "z2m-coordinator";
    private int passwordBytes = 32;
    private int credentialCooldownSeconds = 30;
    private List<String> writableProperties = List.of("state", "brightness", "color_temp");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMqttServer() {
        return mqttServer;
    }

    public void setMqttServer(String mqttServer) {
        this.mqttServer = mqttServer;
    }

    public String getBrokerRole() {
        return brokerRole;
    }

    public void setBrokerRole(String brokerRole) {
        this.brokerRole = brokerRole;
    }

    public int getPasswordBytes() {
        return passwordBytes;
    }

    public void setPasswordBytes(int passwordBytes) {
        this.passwordBytes = passwordBytes;
    }

    public int getCredentialCooldownSeconds() {
        return credentialCooldownSeconds;
    }

    public void setCredentialCooldownSeconds(int credentialCooldownSeconds) {
        this.credentialCooldownSeconds = credentialCooldownSeconds;
    }

    public List<String> getWritableProperties() {
        return writableProperties;
    }

    public void setWritableProperties(List<String> writableProperties) {
        this.writableProperties = writableProperties != null ? List.copyOf(writableProperties) : List.of();
    }
}
