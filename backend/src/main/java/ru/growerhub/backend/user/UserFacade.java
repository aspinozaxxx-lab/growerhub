package ru.growerhub.backend.user;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.auth.AuthFacade;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.common.component.PasswordHasher;
import ru.growerhub.backend.common.config.UserSettings;
import ru.growerhub.backend.device.DeviceFacade;
import ru.growerhub.backend.user.contract.AuthUser;
import ru.growerhub.backend.user.contract.ProductAnalyticsSnapshot;
import ru.growerhub.backend.user.contract.UserProfile;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;

@Service
public class UserFacade {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AuthFacade authFacade;
    private final DeviceFacade deviceFacade;
    private final UserSettings settings;

    public UserFacade(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            @Lazy AuthFacade authFacade,
            @Lazy DeviceFacade deviceFacade,
            UserSettings settings
    ) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.authFacade = authFacade;
        this.deviceFacade = deviceFacade;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public List<UserProfile> listUsers() {
        return userRepository.findAll().stream()
                .map(this::toProfile)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductAnalyticsSnapshot getProductAnalytics() {
        Set<Integer> registeredUserIds = userRepository.findAll().stream()
                .filter(user -> !"admin".equalsIgnoreCase(user.getRole()))
                .map(UserEntity::getId)
                .collect(Collectors.toUnmodifiableSet());
        return new ProductAnalyticsSnapshot(registeredUserIds);
    }

    @Transactional(readOnly = true)
    public UserProfile getUser(Integer userId) {
        if (userId == null) {
            return null;
        }
        UserEntity user = userRepository.findById(userId).orElse(null);
        return user != null ? toProfile(user) : null;
    }

    @Transactional(readOnly = true)
    public UserProfile findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        UserEntity user = userRepository.findByEmail(email).orElse(null);
        return user != null ? toProfile(user) : null;
    }

    @Transactional(readOnly = true)
    public AuthUser getAuthUser(Integer userId) {
        if (userId == null) {
            return null;
        }
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        return new AuthUser(user.getId(), user.getRole(), user.isActive());
    }

    @Transactional
    public UserProfile createUser(String email, String username, String role, String password) {
        UserEntity existing = userRepository.findByEmail(email).orElse(null);
        if (existing != null) {
            throw new DomainException("conflict", "Polzovatel' s takim email uzhe sushhestvuet");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String resolvedRole = role == null || role.isBlank() ? "user" : role;
        UserEntity created = UserEntity.create(
                email,
                username,
                resolvedRole,
                true,
                defaultTimezone(),
                now,
                now
        );
        userRepository.save(created);
        authFacade.createLocalIdentity(created.getId(), passwordHasher.hash(password), now);
        return toProfile(created);
    }

    @Transactional
    public UserProfile createExternalUser(String email, String username) {
        if (email == null || email.isBlank()) {
            throw new DomainException("bad_request", "Email ne ukazan");
        }
        UserEntity existing = userRepository.findByEmail(email).orElse(null);
        if (existing != null) {
            throw new DomainException("conflict", "Polzovatel' s takim email uzhe sushhestvuet");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserEntity created = UserEntity.create(
                email,
                username,
                "user",
                true,
                defaultTimezone(),
                now,
                now
        );
        userRepository.save(created);
        return toProfile(created);
    }

    @Transactional
    public UserProfile updateUser(Integer userId, String username, String role, Boolean active) {
        UserEntity target = userRepository.findById(userId).orElse(null);
        if (target == null) {
            return null;
        }
        boolean changed = false;
        if (username != null) {
            target.setUsername(username);
            changed = true;
        }
        if (role != null) {
            target.setRole(role);
            changed = true;
        }
        if (active != null) {
            target.setActive(active);
            changed = true;
        }
        if (changed) {
            target.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        userRepository.save(target);
        return toProfile(target);
    }

    @Transactional
    public UserProfile updateProfile(Integer userId, String email, String username, String timezone) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }
        boolean changed = false;
        if (email != null) {
            UserEntity existing = userRepository.findByEmail(email).orElse(null);
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
        if (timezone != null) {
            user.setTimezone(normalizeTimezone(timezone));
            changed = true;
        }
        if (changed) {
            user.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        userRepository.save(user);
        return toProfile(user);
    }

    @Transactional(readOnly = true)
    public String getTimezone(Integer userId) {
        UserEntity user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        return user != null ? resolvedTimezone(user) : defaultTimezone();
    }

    @Transactional(readOnly = true)
    public Map<Integer, String> getTimezones(Set<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toUnmodifiableMap(
                        UserEntity::getId,
                        this::resolvedTimezone,
                        (left, right) -> left
                ));
    }

    @Transactional
    public UserProfile markOnboardingCompleted(Integer userId) {
        UserEntity user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        if (user == null) {
            throw new DomainException("not_found", "Polzovatel' ne najden");
        }
        if (user.getOnboardingCompletedAt() == null) {
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            user.setOnboardingCompletedAt(now);
            user.setUpdatedAt(now);
            userRepository.save(user);
        }
        return toProfile(user);
    }

    @Transactional
    public boolean deleteUser(Integer userId) {
        UserEntity target = userRepository.findById(userId).orElse(null);
        if (target == null) {
            return false;
        }
        deviceFacade.unassignDevicesForUser(userId);
        authFacade.deleteIdentities(userId);
        userRepository.delete(target);
        return true;
    }

    private UserProfile toProfile(UserEntity user) {
        return new UserProfile(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                user.isActive(),
                resolvedTimezone(user),
                user.getOnboardingCompletedAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private String resolvedTimezone(UserEntity user) {
        return user.getTimezone() != null && !user.getTimezone().isBlank()
                ? normalizeTimezone(user.getTimezone())
                : defaultTimezone();
    }

    private String defaultTimezone() {
        return normalizeTimezone(settings.getDefaultTimezone());
    }

    private String normalizeTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw new DomainException("bad_request", "Chasovoj pojas ne ukazan");
        }
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (RuntimeException ex) {
            throw new DomainException("bad_request", "Nekorrektnyj chasovoj pojas");
        }
    }
}
