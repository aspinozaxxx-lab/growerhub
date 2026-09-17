package ru.growerhub.backend.auth.engine;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.auth.contract.DemoTokens;
import ru.growerhub.backend.auth.jpa.DemoSessionEntity;
import ru.growerhub.backend.auth.jpa.DemoSessionRepository;
import ru.growerhub.backend.common.config.DemoSettings;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.demo.DemoFacade;
import ru.growerhub.backend.demo.contract.DemoData;
import ru.growerhub.backend.user.UserFacade;

@Service
public class DemoAuthService {
    private final DemoSessionRepository sessions;
    private final DemoFacade demo;
    private final UserFacade users;
    private final JwtService jwt;
    private final DemoSettings settings;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public DemoAuthService(DemoSessionRepository sessions, @Lazy DemoFacade demo, @Lazy UserFacade users,
            JwtService jwt, DemoSettings settings, Clock clock) {
        this.sessions = sessions;
        this.demo = demo;
        this.users = users;
        this.jwt = jwt;
        this.settings = settings;
        this.clock = clock;
    }

    public DemoTokens start(String authorization, String locale, String timezone,
            HttpServletRequest request, HttpServletResponse response) {
        checkOrigin(request);
        Integer accountId = optionalAccount(authorization);
        if (accountId != null) {
            return issue(demo.openSavedOrCreate(accountId, locale, timezone), accountId, response);
        }
        DemoSessionEntity session = cookieSession(request);
        if (session != null && session.accountUserId == null) {
            DemoData.Space space = demo.resumeGuest(session.spaceId, session.generation);
            if (space != null) return tokens(session, space);
        }
        return issue(demo.createGuest(locale, timezone, hash(clientAddress(request))), null, response);
    }

    public DemoTokens refresh(String authorization, HttpServletRequest request) {
        checkOrigin(request);
        DemoSessionEntity session = requireCookieSession(request);
        if (session.accountUserId != null && !session.accountUserId.equals(optionalAccount(authorization))) {
            throw new DomainException("unauthorized", "Vojdite v akkaunt sohranennogo demo");
        }
        DemoData.Space space = demo.authorize(session.spaceId, session.generation, false);
        if (space.saved() != (session.accountUserId != null)) throw expired();
        return tokens(session, space);
    }

    public DemoTokens save(AuthenticatedUser account, boolean replace, HttpServletRequest request,
            HttpServletResponse response) {
        checkOrigin(request);
        if (account == null || account.isDemo()) throw expired();
        DemoSessionEntity session = cookieSession(request);
        if (session == null) throw new DomainException("demo_session_unavailable", "Sessija demo nedostupna");
        if (session.accountUserId != null && !session.accountUserId.equals(account.id())) throw expired();
        DemoData.Space space = demo.save(session.spaceId, session.generation, account.id(), replace);
        sessions.deleteBySpaceId(space.id());
        return issue(space, account.id(), response);
    }

    public DemoTokens reset(AuthenticatedUser user, HttpServletRequest request, HttpServletResponse response) {
        checkOrigin(request);
        if (user == null || !user.isDemo()) throw expired();
        DemoSessionEntity session = requireCookieSession(request);
        DemoData.Space current = demo.authorize(session.spaceId, session.generation, false);
        if (!user.id().equals(current.dataUserId())) throw expired();
        DemoData.Space replacement = demo.reset(current.id(), current.generation());
        sessions.deleteBySpaceId(current.id());
        return issue(replacement, session.accountUserId, response);
    }

    public AuthenticatedUser authenticate(String token, String method, String path) {
        Claims claims;
        try {
            claims = jwt.parseToken(token);
        } catch (RuntimeException ex) {
            return null;
        }
        if (!"DEMO".equals(claims.get("token_use"))) return null;
        try {
            UUID sessionId = UUID.fromString(String.valueOf(claims.get("demo_session_id")));
            DemoSessionEntity session = sessions.findById(sessionId).orElseThrow(this::expired);
            if (!session.expiresAt.isAfter(LocalDateTime.now(clock))
                    || !Objects.equals(session.generation, ((Number) claims.get("demo_generation")).intValue())) throw expired();
            DemoData.Space space = demo.authorize(session.spaceId, session.generation, !java.util.Set.of("GET", "HEAD", "OPTIONS").contains(method));
            if (!Objects.equals(space.dataUserId(), ((Number) claims.get("user_id")).intValue())
                    || space.saved() != (session.accountUserId != null)) throw expired();
            if (session.accountUserId != null) {
                var account = users.getAuthUser(session.accountUserId);
                if (account == null || !account.active()) throw expired();
            }
            if (!DemoAccessPolicy.allows(method, path)) {
                throw new DomainException("forbidden", "Operacija nedostupna v demo");
            }
            return new AuthenticatedUser(space.dataUserId(), "demo");
        } catch (IllegalArgumentException | ClassCastException | NullPointerException ex) {
            throw expired();
        }
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        DemoSessionEntity session = cookieSession(request);
        if (session != null) sessions.delete(session);
        if (request.getCookies() != null && java.util.Arrays.stream(request.getCookies())
                .anyMatch(cookie -> settings.cookieName().equals(cookie.getName()))) {
            writeCookie(response, "", Duration.ZERO);
        }
    }

    public void revokeSpace(UUID spaceId) {
        sessions.deleteBySpaceId(spaceId);
    }

    public void cleanup() {
        sessions.deleteByExpiresAtBefore(LocalDateTime.now(clock));
    }

    private DemoTokens issue(DemoData.Space space, Integer accountId, HttpServletResponse response) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Duration ttl = accountId == null ? Duration.ofHours(settings.guestTtlHours()) : Duration.ofDays(settings.savedSessionDays());
        DemoSessionEntity session = new DemoSessionEntity();
        session.id = UUID.randomUUID();
        session.spaceId = space.id();
        session.generation = space.generation();
        session.accountUserId = accountId;
        session.refreshHash = hash(secret);
        session.expiresAt = LocalDateTime.now(clock).plus(ttl);
        sessions.save(session);
        writeCookie(response, secret, ttl);
        return tokens(session, space);
    }

    private DemoTokens tokens(DemoSessionEntity session, DemoData.Space space) {
        String token = jwt.createToken(Map.of(
                "user_id", space.dataUserId(), "token_use", "DEMO",
                "demo_session_id", session.id.toString(), "demo_generation", session.generation),
                Duration.ofMinutes(settings.accessTokenMinutes()));
        return new DemoTokens(token, space);
    }

    private Integer optionalAccount(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        try {
            Claims claims = jwt.parseToken(authorization.substring(7));
            if (claims.get("token_use") != null) throw expired();
            Integer id = Integer.valueOf(claims.get("user_id").toString());
            var account = users.getAuthUser(id);
            if (account == null || !account.active()) throw expired();
            return id;
        } catch (RuntimeException ex) {
            throw expired();
        }
    }

    private DemoSessionEntity cookieSession(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (!settings.cookieName().equals(cookie.getName()) || cookie.getValue().isBlank()) continue;
            DemoSessionEntity session = sessions.findByRefreshHash(hash(cookie.getValue())).orElse(null);
            return session != null && session.expiresAt.isAfter(LocalDateTime.now(clock)) ? session : null;
        }
        return null;
    }

    private DemoSessionEntity requireCookieSession(HttpServletRequest request) {
        DemoSessionEntity session = cookieSession(request);
        if (session == null) throw expired();
        return session;
    }

    private void checkOrigin(HttpServletRequest request) {
        if (!settings.enabled()) throw new DomainException("unavailable", "Demo poka nedostupno");
        if (!settings.allowedOrigin().equals(request.getHeader(HttpHeaders.ORIGIN))) {
            throw new DomainException("forbidden", "Nedopustimyj istochnik zaprosa");
        }
    }

    private void writeCookie(HttpServletResponse response, String value, Duration ttl) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(settings.cookieName(), value)
                .httpOnly(true).secure(settings.secureCookie()).sameSite("Lax").path("/api").maxAge(ttl).build().toString());
    }

    private String clientAddress(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Real-IP");
        if (settings.trustedProxyAddresses().contains(remote) && forwarded != null
                && forwarded.matches("[0-9a-fA-F:.]{3,45}")) return forwarded;
        return remote;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private DomainException expired() {
        return new DomainException("unauthorized", "Sessija demo istekla");
    }
}
