package ru.growerhub.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.api.ApiException;
import ru.growerhub.backend.api.UserResponse;
import ru.growerhub.backend.api.dto.AuthDtos;
import ru.growerhub.backend.api.dto.CommonDtos;
import ru.growerhub.backend.db.UserAuthIdentityEntity;
import ru.growerhub.backend.db.UserAuthIdentityRepository;
import ru.growerhub.backend.db.UserRefreshTokenEntity;
import ru.growerhub.backend.db.UserRefreshTokenRepository;
import ru.growerhub.backend.user.UserEntity;
import ru.growerhub.backend.user.UserRepository;

@Service
public class AuthService {
    private static final String PROVIDER_LOCAL = "local";

    private final UserRepository userRepository;
    private final UserAuthIdentityRepository identityRepository;
    private final UserRefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository userRepository,
            UserAuthIdentityRepository identityRepository,
            UserRefreshTokenRepository refreshTokenRepository,
            PasswordHasher passwordHasher,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public UserEntity authenticateLocalUser(String email, String password) {
        Optional<UserEntity> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return null;
        }
        UserEntity user = userOpt.get();
        if (!user.isActive()) {
            return null;
        }
        UserAuthIdentityEntity identity = identityRepository
                .findByUser_IdAndProvider(user.getId(), PROVIDER_LOCAL)
                .orElse(null);
        if (identity == null) {
            return null;
        }
        if (!passwordHasher.verify(password, identity.getPasswordHash())) {
            return null;
        }
        return user;
    }

    @Transactional
    public AuthDtos.TokenResponse issueAccessAndRefresh(UserEntity user, HttpServletRequest request, HttpServletResponse response) {
        String accessToken = issueAccessToken(user);
        issueRefreshToken(user, request, response);
        return new AuthDtos.TokenResponse(accessToken, "bearer");
    }

    @Transactional
    public void issueRefreshToken(UserEntity user, HttpServletRequest request, HttpServletResponse response) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String refreshRaw = refreshTokenService.generateToken();
        String refreshHash = refreshTokenService.hashToken(refreshRaw);
        LocalDateTime expiresAt = refreshTokenService.expiresAt(now);
        UserRefreshTokenEntity record = UserRefreshTokenEntity.create(
                user,
                refreshHash,
                now,
                expiresAt,
                request.getHeader("user-agent"),
                request.getRemoteAddr()
        );
        refreshTokenRepository.save(record);
        refreshTokenService.setCookie(response, refreshRaw);
    }

    public String issueAccessToken(UserEntity user) {
        return jwtService.createAccessToken(Map.of("user_id", user.getId()));
    }

    @Transactional
    public AuthDtos.TokenResponse refreshTokens(HttpServletRequest request, HttpServletResponse response) {
        String refreshRaw = refreshTokenService.readToken(request).orElse(null);
        if (refreshRaw == null || refreshRaw.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token required");
        }
        String refreshHash = refreshTokenService.hashToken(refreshRaw);
        UserRefreshTokenEntity record = refreshTokenRepository.findByTokenHash(refreshHash).orElse(null);
        if (record == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        if (record.getRevokedAt() != null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token revoked");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (!record.getExpiresAt().isAfter(now)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }
        UserEntity user = userRepository.findById(record.getUser().getId()).orElse(null);
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "User not found");
        }
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "User disabled");
        }

        record.setRevokedAt(now);
        refreshTokenRepository.save(record);

        String nextRefreshRaw = refreshTokenService.generateToken();
        String nextRefreshHash = refreshTokenService.hashToken(nextRefreshRaw);
        LocalDateTime nextExpiresAt = refreshTokenService.expiresAt(now);
        UserRefreshTokenEntity nextRecord = UserRefreshTokenEntity.create(
                user,
                nextRefreshHash,
                now,
                nextExpiresAt,
                request.getHeader("user-agent"),
                request.getRemoteAddr()
        );
        refreshTokenRepository.save(nextRecord);
        refreshTokenService.setCookie(response, nextRefreshRaw);

        String accessToken = issueAccessToken(user);
        return new AuthDtos.TokenResponse(accessToken, "bearer");
    }

    @Transactional
    public CommonDtos.MessageResponse logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshRaw = refreshTokenService.readToken(request).orElse(null);
        if (refreshRaw != null && !refreshRaw.isEmpty()) {
            String refreshHash = refreshTokenService.hashToken(refreshRaw);
            UserRefreshTokenEntity record = refreshTokenRepository.findByTokenHash(refreshHash).orElse(null);
            if (record != null && record.getRevokedAt() == null) {
                record.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
                refreshTokenRepository.save(record);
            }
        }
        refreshTokenService.clearCookie(response);
        return new CommonDtos.MessageResponse("logged out");
    }

    @Transactional
    public UserResponse updateProfile(UserEntity user, AuthDtos.UserProfileUpdateRequest request) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        boolean changed = false;
        if (request.email() != null) {
            UserEntity existing = userRepository.findByEmail(request.email()).orElse(null);
            if (existing != null && !existing.getId().equals(user.getId())) {
                throw new ApiException(HttpStatus.CONFLICT, "Polzovatel' s takim email uzhe sushhestvuet");
            }
            user.setEmail(request.email());
            changed = true;
        }
        if (request.username() != null) {
            user.setUsername(request.username());
            changed = true;
        }
        if (changed) {
            user.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            userRepository.save(user);
        }
        return toUserResponse(user);
    }

    @Transactional
    public CommonDtos.MessageResponse changePassword(UserEntity user, AuthDtos.PasswordChangeRequest request) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        UserAuthIdentityEntity identity = identityRepository
                .findByUser_IdAndProvider(user.getId(), PROVIDER_LOCAL)
                .orElse(null);
        if (identity == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Local identity ne najdena");
        }
        if (!passwordHasher.verify(request.currentPassword(), identity.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "nevernyj tekushhij parol'");
        }
        identity.setPasswordHash(passwordHasher.hash(request.newPassword()));
        identity.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        identityRepository.save(identity);
        return new CommonDtos.MessageResponse("password updated");
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthMethodsResponse authMethods(UserEntity user) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return buildAuthMethods(user);
    }

    @Transactional
    public AuthDtos.AuthMethodsResponse configureLocal(UserEntity user, AuthDtos.AuthMethodLocalRequest request) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        UserEntity existing = userRepository.findByEmail(request.email()).orElse(null);
        if (existing != null && !existing.getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email uzhe zanyat");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (!request.email().equals(user.getEmail())) {
            user.setEmail(request.email());
            user.setUpdatedAt(now);
        }
        UserAuthIdentityEntity identity = identityRepository
                .findByUser_IdAndProvider(user.getId(), PROVIDER_LOCAL)
                .orElse(null);
        String passwordHash = passwordHasher.hash(request.password());
        if (identity == null) {
            identity = UserAuthIdentityEntity.create(
                    user,
                    PROVIDER_LOCAL,
                    null,
                    passwordHash,
                    now,
                    now
            );
        } else {
            identity.setPasswordHash(passwordHash);
            identity.setUpdatedAt(now);
        }
        userRepository.save(user);
        identityRepository.save(identity);
        return buildAuthMethods(user);
    }

    @Transactional
    public AuthDtos.AuthMethodsResponse deleteMethod(UserEntity user, String provider) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        String normalized = provider == null ? "" : provider;
        if (!normalized.equals("local") && !normalized.equals("google") && !normalized.equals("yandex")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Provider not supported");
        }
        List<UserAuthIdentityEntity> identities = identityRepository.findAllByUser_Id(user.getId());
        if (identities.size() <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "Nelzya udalit's poslednij sposob vhoda");
        }
        UserAuthIdentityEntity identity = identities.stream()
            .filter(item -> normalized.equals(item.getProvider()))
            .findFirst()
            .orElse(null);
        if (identity == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Sposob vhoda ne najden");
        }
        identityRepository.delete(identity);
        return buildAuthMethods(user);
    }

    @Transactional(readOnly = true)
    public UserEntity resolveOptionalUser(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }
        String[] parts = authorizationHeader.split(" ", 2);
        if (parts.length != 2 || !parts[0].equalsIgnoreCase("bearer")) {
            return null;
        }
        String token = parts[1].trim();
        if (token.isEmpty()) {
            return null;
        }
        try {
            Integer userId = parseUserId(jwtService.parseToken(token).get("user_id"));
            if (userId == null) {
                return null;
            }
            return userRepository.findById(userId).orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public UserEntity findUserById(int userId) {
        return userRepository.findById(userId).orElse(null);
    }

    private AuthDtos.AuthMethodsResponse buildAuthMethods(UserEntity user) {
        List<UserAuthIdentityEntity> identities = identityRepository.findAllByUser_Id(user.getId());
        int total = identities.size();
        UserAuthIdentityEntity local = identities.stream()
                .filter(identity -> PROVIDER_LOCAL.equals(identity.getProvider()))
                .findFirst()
                .orElse(null);
        UserAuthIdentityEntity google = identities.stream()
                .filter(identity -> "google".equals(identity.getProvider()))
                .findFirst()
                .orElse(null);
        UserAuthIdentityEntity yandex = identities.stream()
                .filter(identity -> "yandex".equals(identity.getProvider()))
                .findFirst()
                .orElse(null);

        boolean canDelete = total > 1;
        boolean localActive = local != null
                && local.getPasswordHash() != null
                && !local.getPasswordHash().isEmpty();

        AuthDtos.AuthMethodLocalStatus localStatus = new AuthDtos.AuthMethodLocalStatus(
                localActive,
                user.getEmail(),
                local != null && canDelete
        );
        AuthDtos.AuthMethodProviderStatus googleStatus = new AuthDtos.AuthMethodProviderStatus(
                google != null,
                google != null ? google.getProviderSubject() : null,
                google != null && canDelete
        );
        AuthDtos.AuthMethodProviderStatus yandexStatus = new AuthDtos.AuthMethodProviderStatus(
                yandex != null,
                yandex != null ? yandex.getProviderSubject() : null,
                yandex != null && canDelete
        );
        return new AuthDtos.AuthMethodsResponse(localStatus, googleStatus, yandexStatus);
    }

    private Integer parseUserId(Object rawValue) {
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

    private UserResponse toUserResponse(UserEntity user) {
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
}
