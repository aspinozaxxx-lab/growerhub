package ru.growerhub.backend.auth.internal;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenService {
    private final AuthSettings authSettings;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(AuthSettings authSettings) {
        this.authSettings = authSettings;
    }

    public String generateToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashToken(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(authSettings.getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 failure", ex);
        }
    }

    public LocalDateTime expiresAt(LocalDateTime now) {
        LocalDateTime base = now != null ? now : LocalDateTime.now(ZoneOffset.UTC);
        return base.plusDays(authSettings.getRefreshTokenExpireDays());
    }

    public Optional<String> readToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        String name = authSettings.getRefreshTokenCookieName();
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return Optional.ofNullable(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    public void setCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(authSettings.getRefreshTokenCookieName(), refreshToken)
                .httpOnly(true)
                .secure(authSettings.isRefreshTokenCookieSecure())
                .sameSite(authSettings.getRefreshTokenCookieSameSite())
                .path(authSettings.getRefreshTokenCookiePath())
                .maxAge(Duration.ofSeconds((long) authSettings.getRefreshTokenExpireDays() * 24 * 60 * 60))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(authSettings.getRefreshTokenCookieName(), "")
                .path(authSettings.getRefreshTokenCookiePath())
                .sameSite("lax")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
