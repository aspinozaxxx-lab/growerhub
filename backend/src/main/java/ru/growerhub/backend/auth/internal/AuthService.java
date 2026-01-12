package ru.growerhub.backend.auth.internal;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.auth.AuthMethodLocal;
import ru.growerhub.backend.auth.AuthMethodProvider;
import ru.growerhub.backend.auth.AuthMethods;
import ru.growerhub.backend.auth.AuthTokens;
import ru.growerhub.backend.auth.AuthUserProfile;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.common.contract.PasswordHasher;
import ru.growerhub.backend.db.UserAuthIdentityEntity;
import ru.growerhub.backend.db.UserAuthIdentityRepository;
import ru.growerhub.backend.db.UserRefreshTokenEntity;
import ru.growerhub.backend.db.UserRefreshTokenRepository;
import ru.growerhub.backend.user.UserEntity;

@Service
public class AuthService {
    private static final String PROVIDER_LOCAL = "local";

    private final EntityManager entityManager;
    private final UserAuthIdentityRepository identityRepository;
    private final UserRefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            EntityManager entityManager,
            UserAuthIdentityRepository identityRepository,
            UserRefreshTokenRepository refreshTokenRepository,
            PasswordHasher passwordHasher,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.entityManager = entityManager;
        this.identityRepository = identityRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    public UserEntity authenticateLocalUser(String email, String password) {
        Optional<UserEntity> userOpt = findUserByEmail(email);
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

    public AuthTokens issueAccessAndRefresh(UserEntity user, HttpServletRequest request, HttpServletResponse response) {
        String accessToken = issueAccessToken(user);
        issueRefreshToken(user, request, response);
        return new AuthTokens(accessToken, "bearer");
    }

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
        UserEntity user = entityManager.find(UserEntity.class, record.getUser().getId());
        if (user == null) {
            throw new DomainException("unauthorized", "User not found");
        }
        if (!user.isActive()) {
            throw new DomainException("forbidden", "User disabled");
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

    public AuthUserProfile updateProfile(UserEntity user, String email, String username) {
        if (user == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        boolean changed = false;
        if (email != null) {
            UserEntity existing = findUserByEmail(email).orElse(null);
            if (existing != null && !existing.getId().equals(user.getId())) {
                throw new DomainException("conflict", "Polzovatel' s takim email uzhe sushhestvuet");
            }
            user.setEmail(email);
            changed = true;
        }
        if (username != null) {
            user.setUsername(username);
            changed = true;
        }
        if (changed) {
            user.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            entityManager.merge(user);
        }
        return toUserProfile(user);
    }

    public void changePassword(UserEntity user, String currentPassword, String newPassword) {
        if (user == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        UserAuthIdentityEntity identity = identityRepository
                .findByUser_IdAndProvider(user.getId(), PROVIDER_LOCAL)
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

    public AuthMethods authMethods(UserEntity user) {
        if (user == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        return buildAuthMethods(user);
    }

    public AuthMethods configureLocal(UserEntity user, String email, String password) {
        if (user == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        UserEntity existing = findUserByEmail(email).orElse(null);
        if (existing != null && !existing.getId().equals(user.getId())) {
            throw new DomainException("conflict", "Email uzhe zanyat");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (!email.equals(user.getEmail())) {
            user.setEmail(email);
            user.setUpdatedAt(now);
        }
        UserAuthIdentityEntity identity = identityRepository
                .findByUser_IdAndProvider(user.getId(), PROVIDER_LOCAL)
                .orElse(null);
        String passwordHash = passwordHasher.hash(password);
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
        entityManager.merge(user);
        identityRepository.save(identity);
        return buildAuthMethods(user);
    }

    public AuthMethods deleteMethod(UserEntity user, String provider) {
        if (user == null) {
            throw new DomainException("unauthorized", "Not authenticated");
        }
        String normalized = provider == null ? "" : provider;
        if (!normalized.equals("local") && !normalized.equals("google") && !normalized.equals("yandex")) {
            throw new DomainException("not_found", "Provider not supported");
        }
        List<UserAuthIdentityEntity> identities = identityRepository.findAllByUser_Id(user.getId());
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
        return buildAuthMethods(user);
    }

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
            return entityManager.find(UserEntity.class, userId);
        } catch (Exception ex) {
            return null;
        }
    }

    public UserEntity findUserById(int userId) {
        return entityManager.find(UserEntity.class, userId);
    }

    public AuthUserProfile getProfile(Integer userId) {
        if (userId == null) {
            return null;
        }
        UserEntity user = entityManager.find(UserEntity.class, userId);
        return user != null ? toUserProfile(user) : null;
    }

    private Optional<UserEntity> findUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        List<UserEntity> users = entityManager
                .createQuery("select u from UserEntity u where u.email = :email", UserEntity.class)
                .setParameter("email", email)
                .setMaxResults(1)
                .getResultList();
        return users.stream().findFirst();
    }

    private AuthMethods buildAuthMethods(UserEntity user) {
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

        AuthMethodLocal localStatus = new AuthMethodLocal(
                localActive,
                user.getEmail(),
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

    private AuthUserProfile toUserProfile(UserEntity user) {
        return new AuthUserProfile(
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
