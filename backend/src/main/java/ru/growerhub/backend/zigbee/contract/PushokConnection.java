package ru.growerhub.backend.zigbee.contract;

import java.time.LocalDateTime;
import java.util.UUID;

public record PushokConnection(Integer coordinatorId, UUID publicId, String hubId,
        String mqttUsername, String baseTopic, String mqttServer, String encryptedCredentials,
        String hubPublicKey, String status, LocalDateTime attemptAt) {
    @Override public String toString() { return "PushokConnection[" + publicId + ", " + status + "]"; }
}
