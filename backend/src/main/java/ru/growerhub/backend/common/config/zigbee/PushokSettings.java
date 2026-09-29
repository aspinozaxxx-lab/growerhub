package ru.growerhub.backend.common.config.zigbee;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zigbee.pushok")
public class PushokSettings {
    private boolean enabled;
    private String cloudUrl;
    private String encryptionKey;
    private String hubIdPattern;
    private int requestTimeoutSeconds;
    private int reconcileSeconds;
    private int refreshSeconds;
    private int maxConnectionsPerUser;
    private int retrySeconds;
    private int maxMessageBytes;
    private int pairingReservationSeconds;
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getCloudUrl() { return cloudUrl; }
    public void setCloudUrl(String value) { cloudUrl = value; }
    public String getEncryptionKey() { return encryptionKey; }
    public void setEncryptionKey(String value) { encryptionKey = value; }
    public String getHubIdPattern() { return hubIdPattern; }
    public void setHubIdPattern(String value) { hubIdPattern = value; }
    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(int value) { requestTimeoutSeconds = value; }
    public int getReconcileSeconds() { return reconcileSeconds; }
    public void setReconcileSeconds(int value) { reconcileSeconds = value; }
    public int getRefreshSeconds() { return refreshSeconds; }
    public void setRefreshSeconds(int value) { refreshSeconds = value; }
    public int getMaxConnectionsPerUser() { return maxConnectionsPerUser; }
    public void setMaxConnectionsPerUser(int value) { maxConnectionsPerUser = value; }
    public int getRetrySeconds() { return retrySeconds; }
    public void setRetrySeconds(int value) { retrySeconds = value; }
    public int getMaxMessageBytes() { return maxMessageBytes; }
    public void setMaxMessageBytes(int value) { maxMessageBytes = value; }
    public int getPairingReservationSeconds() { return pairingReservationSeconds; }
    public void setPairingReservationSeconds(int value) { pairingReservationSeconds = value; }
}
