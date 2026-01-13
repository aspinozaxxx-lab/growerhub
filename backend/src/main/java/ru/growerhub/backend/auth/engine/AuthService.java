package ru.growerhub.backend.auth.engine;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.auth.contract.AuthMethodLocal;
import ru.growerhub.backend.auth.contract.AuthMethodProvider;
import ru.growerhub.backend.auth.contract.AuthMethods;
import ru.growerhub.backend.auth.contract.AuthTokens;
import ru.growerhub.backend.auth.contract.AuthUserProfile;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.common.contract.PasswordHasher;
import ru.growerhub.backend.db.UserAuthIdentityEntity;
import ru.growerhub.backend.db.UserAuthIdentityRepository;
import ru.growerhub.backend.db.UserRefreshTokenEntity;
import ru.growerhub.backend.db.UserRefreshTokenRepository;
import ru.growerhub.backend.user.UserFacade;

@Service
public class AuthService {
    private static final String PROVIDER_LOCAL = "local";

    private final UserAuthIdentityRepository identityRepository;
    private final UserRefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserFacade userFacade;

    public AuthService(
            UserAuthIdentityRepository identityRepository,
            UserRefreshTokenRepository refreshTokenRepository,
            PasswordHasher passwordHasher,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            UserFacade userFacade
    ) {
        this.identityRepository = identityRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userFacade = userFacade;
    }

    public Integer authenticateLocalUser(String email, String password) {
        UserFacade.UserProfile user = userFacade.findByEmail(email);
        if (user == null) {
            return null;
        }
        if (!user.active()) {
            return null;
        }
        UserAuthIdentityEntity identity = identityRepository
                .findByUserIdAndProvider(user.id(), PROVIDER_LOCAL)
                .orElse(null);
        if (identity == null) {
            return null;
        }
        if (!passwordHasher.verify(password, identity.getPasswordHash())) {
            return null;
        }
        return user.id();
    }

    public AuthTokens issueAccessAndRefresh(Integer userId, HttpServletRequest request, HttpServletResponse response) {
        String accessToken = issueAccessToken(userId);
        issueRefreshToken(userId, request, response);
        return new AuthTokens(accessToken, "bearer");
    }

    public void issueRefreshToken(Integer userId, HttpServletRequest request, HttpServletResponse response) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String refreshRaw = refreshTokenService.generateToken();
        String refreshHash = refreshTokenService.hashToken(refreshRaw);
        LocalDateTime expiresAt = refreshTokenService.expiresAt(now);
        UserRefreshTokenEntity record = UserRefreshTokenEntity.create(
                userId,
                refreshHash,
                now,
                expiresAt,
                request.getHeader("user-agent"),
                request.getRemoteAddr()
        );
        refreshTokenRepository.save(record);
        refreshTokenService.setCookie(response, refreshRaw);
    }

    public String issueAccessToken(Integer userId) {
        return jwtService.createAccessToken(Map.of("user_id", userId));
    }

    public AuthTokens refreshTokens(HttpServletRequest request, HttpServletResponse response) {
        String refreshRaw = refreshTokenService.readToken(request).orElse(null);
        if (refreshRaw == null || refreshRaw.isEmpty()) {
            throw new DomainException("unauthorized", "Refresh token required");
        }
        String refreshHash = refreshTokenService.hashToken(refreshRaw);
        UserRefreshTokenEntity record = refreshTokenRepository.findByTokenHash(refreshHash).orElse(null);
        if (record == null) {
            throw new DomainException("unauthorized", "Invalid refresh token");
        }
        if (record.getRevokedAt() != null) {
            throw new DomainException("unauthorized", "Refresh token revoked");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (!record.getExpiresAt().isAfter(now)) {
            throw new DomainException("unauthorized", "Refresh token expired");
        }
        Integer userId = record.getUserId();
        UserFacade.UserProfile user = userFacade.getUser(userId);
        if (user == null) {
            throw new DomainException("unauthorized", "User not found");
        }
        if (!user.active()) {
            throw new DomainException("forbidden", "User disabled");
        }

        record.setRevokedAt(now);
        refreshTokenRepository.save(record);

        String nextRefreshRaw = refreshTokenService.generateToken();
        String nextRefreshHash = refreshTokenService.hashToken(nextRefreshRaw);
        LocalDateTime nextExpiresAt = refreshTokenService.expiresAt(now);
        UserRefreshTokenEntity nextRecord = UserRefreshTokenEntity.create(
                userId,
                nextRefreshHash,
                now,
                nextExpiresAt,
                request.getHeader("user-agent"),
                request.getRemoteAddr()
        );
        refreshTokenRepository.save(nextRecord);
        refreshTokenService.setCookie(response, nextRefreshRaw);

        String accessToken = issueAccessToken(userId);
        return new AuthTokens(accessToken, "bearer");
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
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
    }

    public AuthUserProfile updateProfile(Integer userId, String email, String username) {
        UserFacade.UserProfile updated = userFacade.updateProfile(userId, email, username);
        if (updated == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        return toUserProfile(updated);
    }

    public void changePassword(Integer userId, String currentPassword, String newPassword) {
        if (userId == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        UserAuthIdentityEntity identity = identityRepository
                .findByUserIdAndProvider(userId, PROVIDER_LOCAL)
                .orElse(null);
        if (identity == null) {
            throw new DomainException("bad_request", "Local identity ne najdena");
        }
        if (!passwordHasher.verify(currentPassword, identity.getPasswordHash())) {
            throw new DomainException("bad_request", "nevernyj tekushhij parol'");
        }
        identity.setPasswordHash(passwordHasher.hash(newPassword));
        identity.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        identityRepository.save(identity);
    }

    public AuthMethods authMethods(Integer userId) {
        if (userId == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        UserFacade.UserProfile user = userFacade.getUser(userId);
        if (user == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        return buildAuthMethods(user);
    }

    public AuthMethods configureLocal(Integer userId, String email, String password) {
        if (userId == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }

        UserFacade.UserProfile user = userFacade.getUser(userId);
        if (user == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (email != null && !email.equals(user.email())) {
            user = userFacade.updateProfile(userId, email, null);
        }
        UserAuthIdentityEntity identity = identityRepository
                .findByUserIdAndProvider(user.id(), PROVIDER_LOCAL)
                .orElse(null);
        String passwordHash = passwordHasher.hash(password);
        if (identity == null) {
            identity = UserAuthIdentityEntity.create(
                    user.id(),
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
        identityRepository.save(identity);
        return buildAuthMethods(user);
    }

    public AuthMethods deleteMethod(Integer userId, String provider) {
        if (userId == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        String normalized = provider == null ? "" : provider;
        if (!normalized.equals("local") && !normalized.equals("google") && !normalized.equals("yandex")) {
            throw new DomainException("not_found", "Provider not supported");
        }
        List<UserAuthIdentityEntity> identities = identityRepository.findAllByUserId(userId);
        if (identities.size() <= 1) {
            throw new DomainException("conflict", "Nelzya udalit's poslednij sposob vhoda");
        }
        UserAuthIdentityEntity identity = identities.stream()
            .filter(item -> normalized.equals(item.getProvider()))
            .findFirst()
            .orElse(null);
        if (identity == null) {
            throw new DomainException("not_found", "Sposob vhoda ne najden");
        }
        identityRepository.delete(identity);
        UserFacade.UserProfile user = userFacade.getUser(userId);
        if (user == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        return buildAuthMethods(user);
    }

    public Integer resolveOptionalUserId(String authorizationHeader) {
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
            UserFacade.AuthUser user = userFacade.getAuthUser(userId);
            return user != null ? user.id() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    public AuthUserProfile getProfile(Integer userId) {
        if (userId == null) {
            return null;
        }
        UserFacade.UserProfile user = userFacade.getUser(userId);
        return user != null ? toUserProfile(user) : null;
    }

    private AuthMethods buildAuthMethods(UserFacade.UserProfile user) {
        List<UserAuthIdentityEntity> identities = identityRepository.findAllByUserId(user.id());
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

        AuthMethodLocal localStatus = new AuthMethodLocal(
                localActive,
                user.email(),
                local != null && canDelete
        );
        AuthMethodProvider googleStatus = new AuthMethodProvider(
                google != null,
                google != null ? google.getProviderSubject() : null,
                google != null && canDelete
        );
        AuthMethodProvider yandexStatus = new AuthMethodProvider(
                yandex != null,
                yandex != null ? yandex.getProviderSubject() : null,
                yandex != null && canDelete
        );
        return new AuthMethods(localStatus, googleStatus, yandexStatus);
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

    private AuthUserProfile toUserProfile(UserFacade.UserProfile user) {
        return new AuthUserProfile(
                user.id(),
                user.email(),
                user.username(),
                user.role(),
                user.active(),
                user.createdAt(),
                user.updatedAt()
        );
    }
}



