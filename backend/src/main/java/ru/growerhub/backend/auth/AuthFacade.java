package ru.growerhub.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.auth.contract.AuthMethods;
import ru.growerhub.backend.auth.contract.AuthTokens;
import ru.growerhub.backend.auth.contract.AuthUserProfile;
import ru.growerhub.backend.auth.engine.AuthService;
import ru.growerhub.backend.auth.engine.JwtService;
import ru.growerhub.backend.auth.engine.SsoService;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.auth.jpa.UserAuthIdentityEntity;

@Service
public class AuthFacade {
    private final AuthService authService;
    private final SsoService ssoService;
    private final JwtService jwtService;

    public AuthFacade(AuthService authService, SsoService ssoService, JwtService jwtService) {
        this.authService = authService;
        this.ssoService = ssoService;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public AuthUserProfile me(AuthenticatedUser user) {
        if (user == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        AuthUserProfile profile = authService.getProfile(user.id());
        if (profile == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        return profile;
    }

    @Transactional
    public AuthTokens login(String email, String password, HttpServletRequest request, HttpServletResponse response) {
        Integer userId = authService.authenticateLocalUser(email, password);
        if (userId == null) {
            return null;
        }
        return authService.issueAccessAndRefresh(userId, request, response);
    }

    @Transactional(readOnly = true)
    public SsoLoginResult ssoLogin(
            String provider,
            String redirectPath,
            String authorization,
            String accept
    ) {
        if (!ssoService.isSupportedProvider(provider)) {
            throw new DomainException("not_found", "Provider not found");
        }
        Integer currentUserId = authService.resolveOptionalUserId(authorization);
        String mode = currentUserId != null ? "link" : "login";
        String resolvedRedirectPath = redirectPath;
        if (resolvedRedirectPath == null || resolvedRedirectPath.isEmpty()) {
            resolvedRedirectPath = "link".equals(mode) ? "/app/profile/" : "/app/onboarding/";
        }
        resolvedRedirectPath = normalizeRedirectPath(resolvedRedirectPath);
        String redirectUri = ssoService.buildCallbackUri(provider);
        String state;
        String authUrl;
        try {
            state = ssoService.buildState(provider, mode, resolvedRedirectPath, currentUserId);
            authUrl = ssoService.buildAuthUrl(provider, redirectUri, state);
        } catch (IllegalArgumentException ex) {
            throw new DomainException("bad_request", "Invalid SSO request");
        }
        boolean wantsJson = accept != null && accept.toLowerCase(Locale.ROOT).contains("application/json");
        return new SsoLoginResult(authUrl, wantsJson);
    }

    @Transactional
    public SsoCallbackResult ssoCallback(
            String provider,
            String code,
            String state,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (!ssoService.isSupportedProvider(provider)) {
            throw new DomainException("not_found", "Provider not found");
        }
        SsoService.SsoState stateData;
        try {
            stateData = ssoService.verifyState(state);
        } catch (SsoService.SsoStateException ex) {
            throw new DomainException("bad_request", "Invalid state");
        }
        if (!provider.equals(stateData.provider())) {
            throw new DomainException("bad_request", "Provider mismatch");
        }

        String redirectUri = ssoService.buildCallbackUri(provider);
        SsoService.SsoProfile profile;
        try {
            Map<String, Object> tokens = ssoService.exchangeCodeForTokens(provider, code, redirectUri);
            profile = ssoService.fetchUserProfile(provider, tokens);
        } catch (SsoService.SsoProviderException ex) {
            throw new DomainException("bad_request", ex.getMessage());
        }
        String subject = profile.subject();
        if (subject == null || subject.isBlank()) {
            throw new DomainException("bad_request", "No subject from provider");
        }

        if ("login".equals(stateData.mode())) {
            SsoService.SsoUserResolution resolution = ssoService.getOrCreateUser(
                    provider,
                    subject,
                    profile.email()
            );
            String redirectTarget = normalizeRedirectPath(stateData.redirectPath());
            authService.issueRefreshToken(resolution.userId(), request, response);
            if (resolution.created()) {
                redirectTarget = appendQueryParameter(redirectTarget, "signup", "complete");
            }
            return new SsoCallbackResult(redirectTarget);
        }

        Object currentUserIdRaw = stateData.currentUserId();
        Integer currentUserId;
        try {
            currentUserId = currentUserIdRaw == null ? null : Integer.parseInt(currentUserIdRaw.toString());
        } catch (NumberFormatException ex) {
            throw new DomainException("unauthorized", "User required for link");
        }
        if (currentUserId == null) {
            throw new DomainException("unauthorized", "User required for link");
        }
        AuthUserProfile currentUser = authService.getProfile(currentUserId);
        if (currentUser == null) {
            throw new DomainException("not_found", "User not found");
        }

        String redirectTarget = normalizeRedirectPath(stateData.redirectPath());

        UserAuthIdentityEntity identitySameSubject = ssoService.findIdentityBySubject(provider, subject);
        if (identitySameSubject != null
                && identitySameSubject.getUserId() != null
                && !identitySameSubject.getUserId().equals(currentUser.id())) {
            throw new DomainException("conflict", "Identity already linked");
        }
        ssoService.linkIdentity(currentUser.id(), provider, subject);
        return new SsoCallbackResult(redirectTarget);
    }

    @Transactional
    public AuthTokens refresh(HttpServletRequest request, HttpServletResponse response) {
        return authService.refreshTokens(request, response);
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
    }

    @Transactional
    public AuthUserProfile updateProfile(AuthenticatedUser user, String email, String username) {
        if (user == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        return authService.updateProfile(user.id(), email, username);
    }

    @Transactional
    public void changePassword(AuthenticatedUser user, String currentPassword, String newPassword) {
        if (user == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        authService.changePassword(user.id(), currentPassword, newPassword);
    }

    @Transactional(readOnly = true)
    public AuthMethods authMethods(AuthenticatedUser user) {
        if (user == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        return authService.authMethods(user.id());
    }

    @Transactional
    public AuthMethods configureLocal(AuthenticatedUser user, String email, String password) {
        if (user == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        return authService.configureLocal(user.id(), email, password);
    }

    @Transactional
    public AuthMethods deleteMethod(AuthenticatedUser user, String provider) {
        if (user == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        return authService.deleteMethod(user.id(), provider);
    }

    public void createLocalIdentity(Integer userId, String passwordHash, LocalDateTime now) {
        authService.createLocalIdentity(userId, passwordHash, now);
    }

    public void deleteIdentities(Integer userId) {
        authService.deleteIdentities(userId);
    }

    public Integer parseUserId(String token) {
        Claims claims;
        try {
            claims = jwtService.parseToken(token);
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
        return parseUserIdClaim(claims.get("user_id"));
    }

    private Integer parseUserIdClaim(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Integer value) {
            return value;
        }
        if (rawValue instanceof Long value) {
            if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
                return null;
            }
            return value.intValue();
        }
        if (rawValue instanceof Number value) {
            return value.intValue();
        }
        if (rawValue instanceof String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private String normalizeRedirectPath(String redirectPath) {
        String candidate = redirectPath != null && !redirectPath.isBlank()
                ? redirectPath.trim()
                : "/app/onboarding/";
        if (candidate.contains("\\") || candidate.chars().anyMatch(Character::isISOControl)) {
            throw new DomainException("bad_request", "Invalid redirect path");
        }
        try {
            URI uri = URI.create(candidate);
            String path = uri.getRawPath();
            if (uri.isAbsolute()
                    || uri.getRawAuthority() != null
                    || uri.getRawFragment() != null
                    || path == null
                    || !("/app".equals(path) || path.startsWith("/app/"))) {
                throw new DomainException("bad_request", "Invalid redirect path");
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException ex) {
            throw new DomainException("bad_request", "Invalid redirect path");
        }
    }

    private String appendQueryParameter(String target, String name, String value) {
        String separator = target.contains("?") ? "&" : "?";
        return target + separator + name + "=" + value;
    }

    public record SsoLoginResult(String authUrl, boolean json) {
    }

    public record SsoCallbackResult(String redirectUrl) {
    }
}

