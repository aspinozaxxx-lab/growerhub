package ru.growerhub.backend.advisor.contract;

import ru.growerhub.backend.common.contract.DomainException;
public final class AssistantLimitException extends DomainException {
    private final int retryAfter;
    public AssistantLimitException(String message, int retryAfter) {
        super("too_many_requests", message); this.retryAfter = Math.max(1, retryAfter);
    }
    public int retryAfter() { return retryAfter; }
}
