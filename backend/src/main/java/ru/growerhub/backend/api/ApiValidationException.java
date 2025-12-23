package ru.growerhub.backend.api;

public class ApiValidationException extends RuntimeException {
    private final ApiValidationError error;

    public ApiValidationException(ApiValidationError error) {
        super("Validation error");
        this.error = error;
    }

    public ApiValidationError getError() {
        return error;
    }
}
