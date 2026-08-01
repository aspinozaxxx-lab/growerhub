package ru.growerhub.backend.device.contract;

import ru.growerhub.backend.common.contract.DomainException;

public class DeviceClaimRejectedException extends DomainException {
    public DeviceClaimRejectedException(String code, String message) {
        super(code, message);
    }
}
