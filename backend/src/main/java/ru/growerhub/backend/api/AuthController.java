package ru.growerhub.backend.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.AuthDtos;
import ru.growerhub.backend.api.dto.CommonDtos;
import ru.growerhub.backend.auth.AuthService;
import ru.growerhub.backend.auth.SsoService;
import ru.growerhub.backend.db.UserAuthIdentityEntity;
import ru.growerhub.backend.user.UserEntity;

@RestController
public class AuthController {

    private final AuthService authService;
    private final SsoService ssoService;

    public AuthController(AuthService authService, SsoService ssoService) {
        this.authService = authService;
        this.ssoService = ssoService;
    }

    @GetMapping("/api/auth/me")
    public UserResponse me(@AuthenticationPrincipal UserEntity user) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<?> login(
            @RequestBody AuthDtos.LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        UserEntity user = authService.authenticateLocalUser(request.email(), request.password());
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    .body(new ApiError("Nevernyj email ili parol'"));
        }
        AuthDtos.TokenResponse token = authService.issueAccessAndRefresh(user, httpRequest, httpResponse);
        return ResponseEntity.ok(token);
    }

    @GetMapping("/api/auth/sso/{provider}/login")
    public ResponseEntity<?> ssoLogin(
            @PathVariable("provider") String provider,
            @RequestParam(value = "redirect_path", required = false) String redirectPath,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "Accept", required = false) String accept
    ) {
        if (!ssoService.isSupportedProvider(provider)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Provider not found");
        }
        UserEntity currentUser = authService.resolveOptionalUser(authorization);
        String mode = currentUser != null ? "link" : "login";
        Integer currentUserId = currentUser != null ? currentUser.getId() : null;
        if (redirectPath == null || redirectPath.isEmpty()) {
            redirectPath = "link".equals(mode) ? "/static/profile.html" : "/app";
        }
        String redirectUri = ssoService.buildCallbackUri(provider);
        String state;
        String authUrl;
        try {
            state = ssoService.buildState(provider, mode, redirectPath, currentUserId);
            authUrl = ssoService.buildAuthUrl(provider, redirectUri, state);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid SSO request");
        }

        if (accept != null && accept.toLowerCase(Locale.ROOT).contains("application/json")) {
            return ResponseEntity.ok(new AuthDtos.SsoLoginResponse(authUrl));
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authUrl)
                .build();
    }

    @GetMapping("/api/auth/sso/{provider}/callback")
    public ResponseEntity<Void> ssoCallback(
            @PathVariable("provider") String provider,
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        if (!ssoService.isSupportedProvider(provider)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Provider not found");
        }
        SsoService.SsoState stateData;
        try {
            stateData = ssoService.verifyState(state);
        } catch (SsoService.SsoStateException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid state");
        }
        if (!provider.equals(stateData.provider())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Provider mismatch");
        }

        String redirectUri = ssoService.buildCallbackUri(provider);
        SsoService.SsoProfile profile;
        try {
            Map<String, Object> tokens = ssoService.exchangeCodeForTokens(provider, code, redirectUri);
            profile = ssoService.fetchUserProfile(provider, tokens);
        } catch (SsoService.SsoProviderException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        String subject = profile.subject();
        if (subject == null || subject.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No subject from provider");
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
            authService.issueRefreshToken(user, httpRequest, httpResponse);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, redirectWithToken)
                    .build();
        }

        Object currentUserIdRaw = stateData.currentUserId();
        Integer currentUserId;
        try {
            currentUserId = currentUserIdRaw == null ? null : Integer.parseInt(currentUserIdRaw.toString());
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User required for link");
        }
        if (currentUserId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User required for link");
        }
        UserEntity currentUser = authService.findUserById(currentUserId);
        if (currentUser == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        }

        String redirectTarget = stateData.redirectPath();
        if (redirectTarget == null || redirectTarget.isEmpty()) {
            redirectTarget = "/static/profile.html";
        }

        UserAuthIdentityEntity identitySameSubject = ssoService.findIdentityBySubject(provider, subject);
        if (identitySameSubject != null
                && identitySameSubject.getUser() != null
                && !identitySameSubject.getUser().getId().equals(currentUser.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Identity already linked");
        }
        ssoService.linkIdentity(currentUser, provider, subject);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectTarget)
                .build();
    }

    @PostMapping("/api/auth/refresh")
    public AuthDtos.TokenResponse refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return authService.refreshTokens(httpRequest, httpResponse);
    }

    @PostMapping("/api/auth/logout")
    public CommonDtos.MessageResponse logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return authService.logout(httpRequest, httpResponse);
    }

    @PatchMapping("/api/auth/me")
    public UserResponse updateMe(
            @RequestBody AuthDtos.UserProfileUpdateRequest request,
            @AuthenticationPrincipal UserEntity user
    ) {
        return authService.updateProfile(user, request);
    }

    @PostMapping("/api/auth/change-password")
    public CommonDtos.MessageResponse changePassword(
            @RequestBody AuthDtos.PasswordChangeRequest request,
            @AuthenticationPrincipal UserEntity user
    ) {
        return authService.changePassword(user, request);
    }

    @GetMapping("/api/auth/methods")
    public AuthDtos.AuthMethodsResponse listMethods(@AuthenticationPrincipal UserEntity user) {
        return authService.authMethods(user);
    }

    @PostMapping("/api/auth/methods/local")
    public AuthDtos.AuthMethodsResponse configureLocal(
            @RequestBody AuthDtos.AuthMethodLocalRequest request,
            @AuthenticationPrincipal UserEntity user
    ) {
        return authService.configureLocal(user, request);
    }

    @DeleteMapping("/api/auth/methods/{provider}")
    public AuthDtos.AuthMethodsResponse deleteMethod(
            @PathVariable("provider") String provider,
            @AuthenticationPrincipal UserEntity user
    ) {
        return authService.deleteMethod(user, provider);
    }
}
