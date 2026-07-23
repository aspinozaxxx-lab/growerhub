package ru.growerhub.backend.zigbee.contract;

public interface ZigbeeBrokerCredentialGateway {
    void provision(String username, String password, String clientId, String roleName);

    void rotate(String username, String password);

    void revoke(String username);
}
