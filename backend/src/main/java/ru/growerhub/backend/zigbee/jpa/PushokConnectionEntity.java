package ru.growerhub.backend.zigbee.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "zigbee_pushok_connections")
public class PushokConnectionEntity {
    @Id @Column(name = "coordinator_id") private Integer coordinatorId;
    @Column(name = "hub_id", nullable = false, unique = true) private String hubId;
    @Column(name = "encrypted_credentials", nullable = false, columnDefinition = "text") private String encryptedCredentials;
    @Column(name = "hub_public_key") private String hubPublicKey;
    @Column(name = "status", nullable = false) private String status;
    @Column(name = "last_error") private String lastError;
    @Column(name = "attempt_at", nullable = false) private LocalDateTime attemptAt;
    protected PushokConnectionEntity() { }
    public PushokConnectionEntity(Integer coordinatorId, String hubId, String credentials, LocalDateTime now) {
        this.coordinatorId = coordinatorId; this.hubId = hubId; encryptedCredentials = credentials;
        status = "PAIRING"; attemptAt = now;
    }
    public Integer getCoordinatorId() { return coordinatorId; }
    public String getHubId() { return hubId; }
    public String getEncryptedCredentials() { return encryptedCredentials; }
    public String getHubPublicKey() { return hubPublicKey; }
    public String getStatus() { return status; }
    public String getLastError() { return lastError; }
    public LocalDateTime getAttemptAt() { return attemptAt; }
    public void requestPairing(LocalDateTime now) { status = "PAIRING"; lastError = null; attemptAt = now; }
    public void paired(String publicKey) { hubPublicKey = publicKey; status = "ACTIVE"; lastError = null; }
    public void fail(String code) { status = "ERROR"; lastError = code; }
}
