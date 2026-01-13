package ru.growerhub.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import ru.growerhub.backend.db.UserAuthIdentityEntity;

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
            resolvedRedirectPath = "link".equals(mode) ? "/static/profile.html" : "/app";
        }
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
            Integer userId = ssoService.getOrCreateUser(provider, subject, profile.email());
            String accessToken = authService.issueAccessToken(userId);
            String redirectTarget = stateData.redirectPath();
            if (redirectTarget == null || redirectTarget.isEmpty()) {
                redirectTarget = "/app";
            }
            String separator = redirectTarget.contains("?") ? "&" : "?";
            String redirectWithToken = redirectTarget + separator + "access_token=" + accessToken;
            authService.issueRefreshToken(userId, request, response);
            return new SsoCallbackResult(redirectWithToken);
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

        String redirectTarget = stateData.redirectPath();
        if (redirectTarget == null || redirectTarget.isEmpty()) {
            redirectTarget = "/static/profile.html";
        }

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

    public record SsoLoginResult(String authUrl, boolean json) {
    }

    public record SsoCallbackResult(String redirectUrl) {
    }
}


