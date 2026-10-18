package ru.growerhub.backend.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import ru.growerhub.backend.advisor.contract.AssistantData.Origin;
import ru.growerhub.backend.common.config.advisor.AssistantSettings;

@Component
public class AssistantOrigin {
    private static final String COOKIE = "gh_assistant_visitor";
    private final AssistantSettings settings;
    public AssistantOrigin(AssistantSettings settings) { this.settings = settings; }
    public Origin resolve(HttpServletRequest request, HttpServletResponse response) {
        boolean proxy = settings.trustedProxies().contains(request.getRemoteAddr());
        String address = normalize(proxy && request.getHeader("X-Real-IP") != null ? request.getHeader("X-Real-IP") : request.getRemoteAddr());
        if (settings.hmacSecret() == null || settings.hmacSecret().length() < 32) return new Origin(address, null, "app");
        String value = request.getCookies() == null ? null : Arrays.stream(request.getCookies())
                .filter(c -> COOKIE.equals(c.getName())).map(jakarta.servlet.http.Cookie::getValue).findFirst().orElse(null);
        if (!valid(value)) {
            String id = UUID.randomUUID().toString(); value = id + "." + sign(id);
            response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, value).httpOnly(true)
                    .secure(request.isSecure() || proxy && "https".equals(request.getHeader("X-Forwarded-Proto")))
                    .sameSite("Lax").path("/api/advisor").maxAge(Duration.ofDays(settings.retentionDays())).build().toString());
        }
        return new Origin(address, value.substring(0, 36), "app");
    }
    private boolean valid(String value) {
        if (value == null || !value.matches("[0-9a-f-]{36}\\.[A-Za-z0-9_-]{43}")) return false;
        try { UUID.fromString(value.substring(0, 36)); }
        catch (IllegalArgumentException ex) { return false; }
        return MessageDigest.isEqual(sign(value.substring(0, 36)).getBytes(StandardCharsets.UTF_8), value.substring(37).getBytes(StandardCharsets.UTF_8));
    }
    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(settings.hmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(("visitor:" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("Visitor signing unavailable", ex); }
    }
    static String normalize(String value) {
        // Tolko IP-literaly: proverka nikogda ne zapuskaet DNS dlya polzovatelskogo znacheniya.
        if (value == null || value.length() > 45 || !(value.matches("\\d{1,3}(?:\\.\\d{1,3}){3}") || value.contains(":") && value.matches("[0-9a-fA-F:.]+"))) return "unknown";
        try {
            byte[] bytes = InetAddress.getByName(value).getAddress();
            if (bytes.length == 16) Arrays.fill(bytes, 8, 16, (byte) 0);
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (Exception ex) { return "unknown"; }
    }
}
