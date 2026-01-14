package ru.growerhub.backend.common.config.auth;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Nastroyki SSO.
@ConfigurationProperties(prefix = "auth.sso")
public class AuthSsoSettings {
    // Spisok podderzhivaemyh providerov.
    private List<String> supportedProviders = List.of("google", "yandex");
    // Timeout soedinenija (sek).
    private int connectTimeoutSeconds = 10;
    // Timeout zaprosa (sek).
    private int requestTimeoutSeconds = 10;
    // Vremja zhizni state-tokena (min).
    private int stateTtlMinutes = 10;

    public List<String> getSupportedProviders() {
        return supportedProviders;
    }

    public void setSupportedProviders(List<String> supportedProviders) {
        this.supportedProviders = supportedProviders;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    public int getStateTtlMinutes() {
        return stateTtlMinutes;
    }

    public void setStateTtlMinutes(int stateTtlMinutes) {
        this.stateTtlMinutes = stateTtlMinutes;
    }
}
