package ru.growerhub.backend.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
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
import ru.growerhub.backend.auth.AuthFacade;
import ru.growerhub.backend.auth.AuthMethodLocal;
import ru.growerhub.backend.auth.AuthMethodProvider;
import ru.growerhub.backend.auth.AuthMethods;
import ru.growerhub.backend.auth.AuthTokens;
import ru.growerhub.backend.auth.AuthUserProfile;
import ru.growerhub.backend.common.AuthenticatedUser;
import ru.growerhub.backend.common.ApiError;

@RestController
public class AuthController {

    private final AuthFacade authFacade;

    public AuthController(AuthFacade authFacade) {
        this.authFacade = authFacade;
    }

    @GetMapping("/api/auth/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return toUserResponse(authFacade.me(user));
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<?> login(
            @RequestBody AuthDtos.LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        AuthTokens tokens = authFacade.login(request.email(), request.password(), httpRequest, httpResponse);
        if (tokens == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    .body(new ApiError("Nevernyj email ili parol'"));
        }
        return ResponseEntity.ok(new AuthDtos.TokenResponse(tokens.accessToken(), tokens.tokenType()));
    }

    @GetMapping("/api/auth/sso/{provider}/login")
    public ResponseEntity<?> ssoLogin(
            @PathVariable("provider") String provider,
            @RequestParam(value = "redirect_path", required = false) String redirectPath,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "Accept", required = false) String accept
    ) {
        AuthFacade.SsoLoginResult result = authFacade.ssoLogin(provider, redirectPath, authorization, accept);
        if (result.json()) {
            return ResponseEntity.ok(new AuthDtos.SsoLoginResponse(result.authUrl()));
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, result.authUrl())
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
        AuthFacade.SsoCallbackResult result = authFacade.ssoCallback(provider, code, state, httpRequest, httpResponse);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, result.redirectUrl())
                .build();
    }

    @PostMapping("/api/auth/refresh")
    public AuthDtos.TokenResponse refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        AuthTokens tokens = authFacade.refresh(httpRequest, httpResponse);
        return new AuthDtos.TokenResponse(tokens.accessToken(), tokens.tokenType());
    }

    @PostMapping("/api/auth/logout")
    public CommonDtos.MessageResponse logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authFacade.logout(httpRequest, httpResponse);
        return new CommonDtos.MessageResponse("logged out");
    }

    @PatchMapping("/api/auth/me")
    public UserResponse updateMe(
            @RequestBody AuthDtos.UserProfileUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        AuthUserProfile profile = authFacade.updateProfile(user, request.email(), request.username());
        return toUserResponse(profile);
    }

    @PostMapping("/api/auth/change-password")
    public CommonDtos.MessageResponse changePassword(
            @RequestBody AuthDtos.PasswordChangeRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        authFacade.changePassword(user, request.currentPassword(), request.newPassword());
        return new CommonDtos.MessageResponse("password updated");
    }

    @GetMapping("/api/auth/methods")
    public AuthDtos.AuthMethodsResponse listMethods(@AuthenticationPrincipal AuthenticatedUser user) {
        return toAuthMethodsResponse(authFacade.authMethods(user));
    }

    @PostMapping("/api/auth/methods/local")
    public AuthDtos.AuthMethodsResponse configureLocal(
            @RequestBody AuthDtos.AuthMethodLocalRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return toAuthMethodsResponse(authFacade.configureLocal(user, request.email(), request.password()));
    }

    @DeleteMapping("/api/auth/methods/{provider}")
    public AuthDtos.AuthMethodsResponse deleteMethod(
            @PathVariable("provider") String provider,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return toAuthMethodsResponse(authFacade.deleteMethod(user, provider));
    }

    private UserResponse toUserResponse(AuthUserProfile profile) {
        return new UserResponse(
                profile.id(),
                profile.email(),
                profile.username(),
                profile.role(),
                profile.active(),
                profile.createdAt(),
                profile.updatedAt()
        );
    }

    private AuthDtos.AuthMethodsResponse toAuthMethodsResponse(AuthMethods methods) {
        AuthMethodLocal local = methods.local();
        AuthMethodProvider google = methods.google();
        AuthMethodProvider yandex = methods.yandex();
        AuthDtos.AuthMethodLocalStatus localStatus = new AuthDtos.AuthMethodLocalStatus(
                local.active(),
                local.email(),
                local.canDelete()
        );
        AuthDtos.AuthMethodProviderStatus googleStatus = new AuthDtos.AuthMethodProviderStatus(
                google.active(),
                google.providerSubject(),
                google.canDelete()
        );
        AuthDtos.AuthMethodProviderStatus yandexStatus = new AuthDtos.AuthMethodProviderStatus(
                yandex.active(),
                yandex.providerSubject(),
                yandex.canDelete()
        );
        return new AuthDtos.AuthMethodsResponse(localStatus, googleStatus, yandexStatus);
    }
}
