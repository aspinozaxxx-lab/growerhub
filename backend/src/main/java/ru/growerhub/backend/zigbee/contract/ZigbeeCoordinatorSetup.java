package ru.growerhub.backend.zigbee.contract;

public record ZigbeeCoordinatorSetup(
        String server,
        String username,
        String password,
        String clientId,
        String baseTopic,
        String configurationYaml,
        String secretYaml
) {
}
