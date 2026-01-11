package ru.growerhub.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.auth.internal.AuthService;
import ru.growerhub.backend.auth.internal.SsoService;
import ru.growerhub.backend.common.AuthenticatedUser;
import ru.growerhub.backend.common.DomainException;
import ru.growerhub.backend.db.UserAuthIdentityEntity;
import ru.growerhub.backend.user.UserEntity;

@Service
public class AuthFacade {
    private final AuthService authService;
    private final SsoService ssoService;

    public AuthFacade(AuthService authService, SsoService ssoService) {
        this.authService = authService;
        this.ssoService = ssoService;
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
        UserEntity user = authService.authenticateLocalUser(email, password);
        if (user == null) {
            return null;
        }
        return authService.issueAccessAndRefresh(user, request, response);
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
        UserEntity currentUser = authService.resolveOptionalUser(authorization);
        String mode = currentUser != null ? "link" : "login";
        Integer currentUserId = currentUser != null ? currentUser.getId() : null;
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
            UserEntity user = ssoService.getOrCreateUser(provider, subject, profile.email());
            String accessToken = authService.issueAccessToken(user);
            String redirectTarget = stateData.redirectPath();
            if (redirectTarget == null || redirectTarget.isEmpty()) {
                redirectTarget = "/app";
            }
            String separator = redirectTarget.contains("?") ? "&" : "?";
            String redirectWithToken = redirectTarget + separator + "access_token=" + accessToken;
            authService.issueRefreshToken(user, request, response);
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
        UserEntity currentUser = authService.findUserById(currentUserId);
        if (currentUser == null) {
            throw new DomainException("not_found", "User not found");
        }

        String redirectTarget = stateData.redirectPath();
        if (redirectTarget == null || redirectTarget.isEmpty()) {
            redirectTarget = "/static/profile.html";
        }

        UserAuthIdentityEntity identitySameSubject = ssoService.findIdentityBySubject(provider, subject);
        if (identitySameSubject != null
                && identitySameSubject.getUser() != null
                && !identitySameSubject.getUser().getId().equals(currentUser.getId())) {
            throw new DomainException("conflict", "Identity already linked");
        }
        ssoService.linkIdentity(currentUser, provider, subject);
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
        UserEntity entity = authService.findUserById(user.id());
        if (entity == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        return authService.updateProfile(entity, email, username);
    }

    @Transactional
    public void changePassword(AuthenticatedUser user, String currentPassword, String newPassword) {
        if (user == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        UserEntity entity = authService.findUserById(user.id());
        if (entity == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        authService.changePassword(entity, currentPassword, newPassword);
    }

    @Transactional(readOnly = true)
    public AuthMethods authMethods(AuthenticatedUser user) {
        if (user == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        UserEntity entity = authService.findUserById(user.id());
        if (entity == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        return authService.authMethods(entity);
    }

    @Transactional
    public AuthMethods configureLocal(AuthenticatedUser user, String email, String password) {
        if (user == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        UserEntity entity = authService.findUserById(user.id());
        if (entity == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        return authService.configureLocal(entity, email, password);
    }

    @Transactional
    public AuthMethods deleteMethod(AuthenticatedUser user, String provider) {
        if (user == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        UserEntity entity = authService.findUserById(user.id());
        if (entity == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        return authService.deleteMethod(entity, provider);
    }

    public record SsoLoginResult(String authUrl, boolean json) {
    }

    public record SsoCallbackResult(String redirectUrl) {
    }
}
