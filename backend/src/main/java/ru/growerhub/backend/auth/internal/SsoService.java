package ru.growerhub.backend.auth.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.db.UserAuthIdentityEntity;
import ru.growerhub.backend.db.UserAuthIdentityRepository;
import ru.growerhub.backend.user.UserEntity;

@Service
public class SsoService {
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("google", "yandex");

    private final AuthSettings authSettings;
    private final JwtService jwtService;
    private final EntityManager entityManager;
    private final UserAuthIdentityRepository identityRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SsoService(
            AuthSettings authSettings,
            JwtService jwtService,
            EntityManager entityManager,
            UserAuthIdentityRepository identityRepository,
            ObjectMapper objectMapper
    ) {
        this.authSettings = authSettings;
        this.jwtService = jwtService;
        this.entityManager = entityManager;
        this.identityRepository = identityRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isSupportedProvider(String provider) {
        return provider != null && SUPPORTED_PROVIDERS.contains(provider);
    }

    public String buildCallbackUri(String provider) {
        String path = "/api/auth/sso/" + provider + "/callback";
        String base = authSettings.getAuthSsoRedirectBase();
        if (base == null || base.isBlank()) {
            return path;
        }
        return base.replaceAll("/+$", "") + path;
    }

    public String buildState(String provider, String mode, String redirectPath, Integer currentUserId) {
        validateProvider(provider);
        validateMode(mode);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", provider);
        payload.put("mode", mode);
        payload.put("redirect_path", redirectPath);
        payload.put("current_user_id", currentUserId);
        return jwtService.createToken(payload, Duration.ofMinutes(10));
    }

    public SsoState verifyState(String state) {
        Map<String, Object> payload;
        try {
            payload = jwtService.parseToken(state);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new SsoStateException();
        }
        String provider = Optional.ofNullable(payload.get("provider"))
                .map(Object::toString)
                .orElse(null);
        String mode = Optional.ofNullable(payload.get("mode"))
                .map(Object::toString)
                .orElse(null);
        String redirectPath = Optional.ofNullable(payload.get("redirect_path"))
                .map(Object::toString)
                .orElse(null);
        Object currentUserId = payload.get("current_user_id");
        if (provider == null || !SUPPORTED_PROVIDERS.contains(provider)) {
            throw new SsoStateException();
        }
        if (mode == null || (!mode.equals("login") && !mode.equals("link"))) {
            throw new SsoStateException();
        }
        return new SsoState(provider, mode, redirectPath, currentUserId);
    }

    public String buildAuthUrl(String provider, String redirectUri, String state) {
        ProviderConfig cfg = providerConfig(provider);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("response_type", "code");
        params.put("client_id", cfg.clientId());
        params.put("redirect_uri", redirectUri);
        params.put("scope", cfg.scope());
        params.put("state", state);
        if ("google".equals(provider)) {
            params.put("access_type", cfg.accessType());
            params.put("prompt", cfg.prompt());
            params.put("include_granted_scopes", "true");
        }
        return cfg.authUrl() + "?" + encodeParams(params);
    }

    public Map<String, Object> exchangeCodeForTokens(String provider, String code, String redirectUri) {
        ProviderConfig cfg = providerConfig(provider);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("grant_type", "authorization_code");
        data.put("code", code);
        data.put("redirect_uri", redirectUri);
        data.put("client_id", cfg.clientId());
        data.put("client_secret", cfg.clientSecret());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(cfg.tokenUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encodeParams(data)))
                .build();
        HttpResponse<String> response = sendRequest(request, "Failed to exchange code for tokens");
        if (response.statusCode() >= 400) {
            throw new SsoProviderException("Failed to exchange code for tokens");
        }
        try {
            return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        } catch (IOException ex) {
            throw new SsoProviderException("Failed to exchange code for tokens");
        }
    }

    public SsoProfile fetchUserProfile(String provider, Map<String, Object> tokens) {
        ProviderConfig cfg = providerConfig(provider);
        Object rawToken = tokens.get("access_token");
        if (rawToken == null || rawToken.toString().isBlank()) {
            throw new SsoProviderException("No access token provided");
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(cfg.userinfoUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + rawToken)
                .GET()
                .build();
        HttpResponse<String> response = sendRequest(request, "Failed to fetch userinfo");
        if (response.statusCode() >= 400) {
            throw new SsoProviderException("Failed to fetch userinfo");
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
            if ("google".equals(provider)) {
                String subject = Optional.ofNullable(raw.get("sub")).map(Object::toString).orElse(null);
                String email = Optional.ofNullable(raw.get("email")).map(Object::toString).orElse(null);
                Object verified = raw.get("email_verified");
                Boolean emailVerified = verified == null ? null : Boolean.valueOf(verified.toString());
                return new SsoProfile(subject, email, emailVerified);
            }
            String subject = Optional.ofNullable(raw.get("id"))
                    .or(() -> Optional.ofNullable(raw.get("uid")))
                    .map(Object::toString)
                    .orElse(null);
            String email = Optional.ofNullable(raw.get("default_email")).map(Object::toString).orElse(null);
            if (email == null && raw.get("emails") instanceof Iterable<?> emails) {
                for (Object value : emails) {
                    if (value != null) {
                        email = value.toString();
                        break;
                    }
                }
            }
            Object verified = raw.get("is_email_verified");
            Boolean emailVerified = verified == null ? null : Boolean.valueOf(verified.toString());
            return new SsoProfile(subject, email, emailVerified);
        } catch (IOException ex) {
            throw new SsoProviderException("Failed to fetch userinfo");
        }
    }

    @Transactional
    public UserEntity getOrCreateUser(String provider, String subject, String email) {
        validateProvider(provider);
        UserAuthIdentityEntity identity = identityRepository
                .findByProviderAndProviderSubject(provider, subject)
                .orElse(null);
        if (identity != null) {
            return identity.getUser();
        }

        String normalizedEmail = (email != null && !email.isEmpty()) ? email : null;
        UserEntity existingEmailUser = normalizedEmail != null ? findUserByEmail(normalizedEmail).orElse(null) : null;
        String userEmail = normalizedEmail != null ? normalizedEmail : provider + "_" + subject + "@example.invalid";
        if (existingEmailUser != null) {
            userEmail = provider + "_" + subject + "@example.invalid";
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserEntity user = UserEntity.create(userEmail, null, "user", true, now, now);
        entityManager.persist(user);

        UserAuthIdentityEntity newIdentity = UserAuthIdentityEntity.create(
                user,
                provider,
                subject,
                null,
                now,
                now
        );
        identityRepository.save(newIdentity);
        return user;
    }

    public UserAuthIdentityEntity findIdentityBySubject(String provider, String subject) {
        return identityRepository.findByProviderAndProviderSubject(provider, subject).orElse(null);
    }

    public UserAuthIdentityEntity findIdentityByProvider(Integer userId, String provider) {
        return identityRepository.findByUser_IdAndProvider(userId, provider).orElse(null);
    }

    @Transactional
    public UserAuthIdentityEntity linkIdentity(UserEntity user, String provider, String subject) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserAuthIdentityEntity identity = identityRepository
                .findByUser_IdAndProvider(user.getId(), provider)
                .orElse(null);
        if (identity == null) {
            identity = UserAuthIdentityEntity.create(user, provider, subject, null, now, now);
        } else {
            identity.setProviderSubject(subject);
            identity.setPasswordHash(null);
            identity.setUpdatedAt(now);
        }
        return identityRepository.save(identity);
    }

    private ProviderConfig providerConfig(String provider) {
        if ("google".equals(provider)) {
            return new ProviderConfig(
                    authSettings.getGoogleClientId(),
                    authSettings.getGoogleClientSecret(),
                    authSettings.getGoogleAuthUrl(),
                    authSettings.getGoogleTokenUrl(),
                    authSettings.getGoogleUserinfoUrl(),
                    "openid email profile",
                    "offline",
                    "consent"
            );
        }
        if ("yandex".equals(provider)) {
            return new ProviderConfig(
                    authSettings.getYandexClientId(),
                    authSettings.getYandexClientSecret(),
                    authSettings.getYandexAuthUrl(),
                    authSettings.getYandexTokenUrl(),
                    authSettings.getYandexUserinfoUrl(),
                    "login:email",
                    null,
                    null
            );
        }
        throw new IllegalArgumentException("Unsupported provider");
    }

    private void validateProvider(String provider) {
        if (!isSupportedProvider(provider)) {
            throw new IllegalArgumentException("Unsupported provider");
        }
    }

    private void validateMode(String mode) {
        if (!"login".equals(mode) && !"link".equals(mode)) {
            throw new IllegalArgumentException("Unsupported mode");
        }
    }

    private String encodeParams(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private Optional<UserEntity> findUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return entityManager
                .createQuery("select u from UserEntity u where u.email = :email", UserEntity.class)
                .setParameter("email", email)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    private HttpResponse<String> sendRequest(HttpRequest request, String errorDetail) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SsoProviderException(errorDetail);
        }
    }

    public record SsoState(String provider, String mode, String redirectPath, Object currentUserId) {
    }

    public record SsoProfile(String subject, String email, Boolean emailVerified) {
    }

    private record ProviderConfig(
            String clientId,
            String clientSecret,
            String authUrl,
            String tokenUrl,
            String userinfoUrl,
            String scope,
            String accessType,
            String prompt
    ) {
    }

    public static class SsoProviderException extends RuntimeException {
        public SsoProviderException(String message) {
            super(message);
        }
    }

    public static class SsoStateException extends RuntimeException {
    }
}
