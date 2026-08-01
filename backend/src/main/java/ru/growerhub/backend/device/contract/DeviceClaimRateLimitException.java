package ru.growerhub.backend.device.contract;

import ru.growerhub.backend.common.contract.DomainException;

public class DeviceClaimRateLimitException extends DomainException {
    private final long retryAfterSeconds;

    public DeviceClaimRateLimitException(long retryAfterSeconds) {
        super("too_many_requests", "Слишком много попыток. Повторите позже");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
