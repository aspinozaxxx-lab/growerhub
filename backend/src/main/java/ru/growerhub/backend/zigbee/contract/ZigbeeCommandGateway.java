package ru.growerhub.backend.zigbee.contract;

import java.util.Map;

public interface ZigbeeCommandGateway {
    void publishPermitJoin(String baseTopic, int seconds);

    void publishSet(String baseTopic, String friendlyName, Map<String, Object> payload);

    void publishRename(String baseTopic, String fromFriendlyName, String toFriendlyName);
}
