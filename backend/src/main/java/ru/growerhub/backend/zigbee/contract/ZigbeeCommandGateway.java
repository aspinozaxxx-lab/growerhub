package ru.growerhub.backend.zigbee.contract;

public interface ZigbeeCommandGateway {
    void publishPermitJoin(int seconds);

    void publishSetState(String friendlyName, String state);

    void publishRename(String fromFriendlyName, String toFriendlyName);
}
