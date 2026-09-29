package ru.growerhub.backend.zigbee.contract;

import java.util.UUID;

public interface PushokCredentialGateway {
    String createEncryptedCredentials(UUID coordinatorId, String mqttPassword);
}
