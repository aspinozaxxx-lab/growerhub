package ru.growerhub.backend.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.AuthDtos;
import ru.growerhub.backend.api.dto.CommonDtos;
import ru.growerhub.backend.user.UserEntity;

@RestController
public class AuthController {

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
    public AuthDtos.TokenResponse login(@RequestBody AuthDtos.LoginRequest request) {
        throw todo();
    }

    @GetMapping("/api/auth/sso/{provider}/login")
    public AuthDtos.SsoLoginResponse ssoLogin(
            @PathVariable("provider") String provider,
            @RequestParam(value = "redirect_path", required = false) String redirectPath
    ) {
        throw todo();
    }

    @GetMapping("/api/auth/sso/{provider}/callback")
    public ResponseEntity<Void> ssoCallback(
            @PathVariable("provider") String provider,
            @RequestParam("code") String code,
            @RequestParam("state") String state
    ) {
        throw todo();
    }

    @PostMapping("/api/auth/refresh")
    public AuthDtos.TokenResponse refresh() {
        throw todo();
    }

    @PostMapping("/api/auth/logout")
    public CommonDtos.MessageResponse logout() {
        throw todo();
    }

    @PatchMapping("/api/auth/me")
    public UserResponse updateMe(@RequestBody AuthDtos.UserProfileUpdateRequest request) {
        throw todo();
    }

    @PostMapping("/api/auth/change-password")
    public CommonDtos.MessageResponse changePassword(
            @RequestBody AuthDtos.PasswordChangeRequest request
    ) {
        throw todo();
    }

    @GetMapping("/api/auth/methods")
    public AuthDtos.AuthMethodsResponse listMethods() {
        throw todo();
    }

    @PostMapping("/api/auth/methods/local")
    public AuthDtos.AuthMethodsResponse configureLocal(
            @RequestBody AuthDtos.AuthMethodLocalRequest request
    ) {
        throw todo();
    }

    @DeleteMapping("/api/auth/methods/{provider}")
    public AuthDtos.AuthMethodsResponse deleteMethod(
            @PathVariable("provider") String provider
    ) {
        throw todo();
    }

    private static ApiException todo() {
        return new ApiException(HttpStatus.NOT_IMPLEMENTED, "TODO");
    }
}
