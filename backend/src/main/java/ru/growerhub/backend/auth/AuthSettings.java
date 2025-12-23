package ru.growerhub.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthSettings {
    private final String secretKey;
    private final int accessTokenExpireMinutes;
    private final int refreshTokenExpireDays;
    private final String refreshTokenCookieName;
    private final boolean refreshTokenCookieSecure;
    private final String refreshTokenCookieSameSite;
    private final String refreshTokenCookiePath;
    private final String authSsoRedirectBase;
    private final String googleClientId;
    private final String googleClientSecret;
    private final String googleAuthUrl;
    private final String googleTokenUrl;
    private final String googleUserinfoUrl;
    private final String yandexClientId;
    private final String yandexClientSecret;
    private final String yandexAuthUrl;
    private final String yandexTokenUrl;
    private final String yandexUserinfoUrl;

    public AuthSettings(
            @Value("${SECRET_KEY:${JWT_SECRET_KEY:}}") String secretKey,
            @Value("${ACCESS_TOKEN_EXPIRE_MINUTES:60}") int accessTokenExpireMinutes,
            @Value("${REFRESH_TOKEN_EXPIRE_DAYS:30}") int refreshTokenExpireDays,
            @Value("${REFRESH_TOKEN_COOKIE_NAME:gh_refresh_token}") String refreshTokenCookieName,
            @Value("${REFRESH_TOKEN_COOKIE_SECURE:false}") boolean refreshTokenCookieSecure,
            @Value("${REFRESH_TOKEN_COOKIE_SAMESITE:lax}") String refreshTokenCookieSameSite,
            @Value("${REFRESH_TOKEN_COOKIE_PATH:/api/auth}") String refreshTokenCookiePath,
            @Value("${AUTH_SSO_REDIRECT_BASE:}") String authSsoRedirectBase,
            @Value("${AUTH_GOOGLE_CLIENT_ID:}") String googleClientId,
            @Value("${AUTH_GOOGLE_CLIENT_SECRET:}") String googleClientSecret,
            @Value("${AUTH_GOOGLE_AUTH_URL:https://accounts.google.com/o/oauth2/v2/auth}") String googleAuthUrl,
            @Value("${AUTH_GOOGLE_TOKEN_URL:https://oauth2.googleapis.com/token}") String googleTokenUrl,
            @Value("${AUTH_GOOGLE_USERINFO_URL:https://openidconnect.googleapis.com/v1/userinfo}") String googleUserinfoUrl,
            @Value("${AUTH_YANDEX_CLIENT_ID:}") String yandexClientId,
            @Value("${AUTH_YANDEX_CLIENT_SECRET:}") String yandexClientSecret,
            @Value("${AUTH_YANDEX_AUTH_URL:https://oauth.yandex.ru/authorize}") String yandexAuthUrl,
            @Value("${AUTH_YANDEX_TOKEN_URL:https://oauth.yandex.ru/token}") String yandexTokenUrl,
            @Value("${AUTH_YANDEX_USERINFO_URL:https://login.yandex.ru/info}") String yandexUserinfoUrl
    ) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("SECRET_KEY is required");
        }
        this.secretKey = secretKey;
        this.accessTokenExpireMinutes = accessTokenExpireMinutes;
        this.refreshTokenExpireDays = refreshTokenExpireDays;
        this.refreshTokenCookieName = refreshTokenCookieName;
        this.refreshTokenCookieSecure = refreshTokenCookieSecure;
        this.refreshTokenCookieSameSite = normalizeSameSite(refreshTokenCookieSameSite);
        this.refreshTokenCookiePath = refreshTokenCookiePath;
        this.authSsoRedirectBase = authSsoRedirectBase != null && !authSsoRedirectBase.isBlank()
                ? authSsoRedirectBase
                : null;
        this.googleClientId = googleClientId;
        this.googleClientSecret = googleClientSecret;
        this.googleAuthUrl = googleAuthUrl;
        this.googleTokenUrl = googleTokenUrl;
        this.googleUserinfoUrl = googleUserinfoUrl;
        this.yandexClientId = yandexClientId;
        this.yandexClientSecret = yandexClientSecret;
        this.yandexAuthUrl = yandexAuthUrl;
        this.yandexTokenUrl = yandexTokenUrl;
        this.yandexUserinfoUrl = yandexUserinfoUrl;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public int getAccessTokenExpireMinutes() {
        return accessTokenExpireMinutes;
    }

    public int getRefreshTokenExpireDays() {
        return refreshTokenExpireDays;
    }

    public String getRefreshTokenCookieName() {
        return refreshTokenCookieName;
    }

    public boolean isRefreshTokenCookieSecure() {
        return refreshTokenCookieSecure;
    }

    public String getRefreshTokenCookieSameSite() {
        return refreshTokenCookieSameSite;
    }

    public String getRefreshTokenCookiePath() {
        return refreshTokenCookiePath;
    }

    public String getAuthSsoRedirectBase() {
        return authSsoRedirectBase;
    }

    public String getGoogleClientId() {
        return googleClientId;
    }

    public String getGoogleClientSecret() {
        return googleClientSecret;
    }

    public String getGoogleAuthUrl() {
        return googleAuthUrl;
    }

    public String getGoogleTokenUrl() {
        return googleTokenUrl;
    }

    public String getGoogleUserinfoUrl() {
        return googleUserinfoUrl;
    }

    public String getYandexClientId() {
        return yandexClientId;
    }

    public String getYandexClientSecret() {
        return yandexClientSecret;
    }

    public String getYandexAuthUrl() {
        return yandexAuthUrl;
    }

    public String getYandexTokenUrl() {
        return yandexTokenUrl;
    }

    public String getYandexUserinfoUrl() {
        return yandexUserinfoUrl;
    }

    private static String normalizeSameSite(String value) {
        if (value == null) {
            return "lax";
        }
        String normalized = value.trim().toLowerCase();
        if (normalized.equals("lax") || normalized.equals("strict") || normalized.equals("none")) {
            return normalized;
        }
        return "lax";
    }
}
