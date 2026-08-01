package ru.growerhub.backend.common.config.device;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "device.mqtt")
public class DeviceMqttSettings {
    private String publicHost = "growerhub.ru";
    private int publicPort = 8883;
    private boolean tls = true;
    private String brokerRole = "native-device";
    private int credentialCooldownSeconds = 30;

    public String getPublicHost() {
        return publicHost;
    }

    public void setPublicHost(String publicHost) {
        this.publicHost = publicHost;
    }

    public int getPublicPort() {
        return publicPort;
    }

    public void setPublicPort(int publicPort) {
        this.publicPort = publicPort;
    }

    public boolean isTls() {
        return tls;
    }

    public void setTls(boolean tls) {
        this.tls = tls;
    }

    public String getBrokerRole() {
        return brokerRole;
    }

    public void setBrokerRole(String brokerRole) {
        this.brokerRole = brokerRole;
    }

    public int getCredentialCooldownSeconds() {
        return credentialCooldownSeconds;
    }

    public void setCredentialCooldownSeconds(int credentialCooldownSeconds) {
        this.credentialCooldownSeconds = credentialCooldownSeconds;
    }
}
