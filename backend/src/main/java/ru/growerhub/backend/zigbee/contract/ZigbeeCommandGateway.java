package ru.growerhub.backend.zigbee.contract;

import java.util.Map;

public interface ZigbeeCommandGateway {
    void publishPermitJoin(int seconds);

    void publishSetState(String friendlyName, String state);

    void publishSet(String friendlyName, Map<String, Object> payload);

    void publishRename(String fromFriendlyName, String toFriendlyName);
}
