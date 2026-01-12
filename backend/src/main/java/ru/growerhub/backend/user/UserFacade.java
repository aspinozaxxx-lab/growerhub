package ru.growerhub.backend.user;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.common.component.PasswordHasher;
import ru.growerhub.backend.db.UserAuthIdentityEntity;
import ru.growerhub.backend.db.UserAuthIdentityRepository;
import ru.growerhub.backend.device.DeviceFacade;
import ru.growerhub.backend.user.internal.UserRepository;

@Service
public class UserFacade {
    private final UserRepository userRepository;
    private final UserAuthIdentityRepository identityRepository;
    private final PasswordHasher passwordHasher;
    private final DeviceFacade deviceFacade;

    public UserFacade(
            UserRepository userRepository,
            UserAuthIdentityRepository identityRepository,
            PasswordHasher passwordHasher,
            DeviceFacade deviceFacade
    ) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.passwordHasher = passwordHasher;
        this.deviceFacade = deviceFacade;
    }

    @Transactional(readOnly = true)
    public List<UserProfile> listUsers() {
        return userRepository.findAll().stream()
                .map(this::toProfile)
                .toList();
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
                now,
                now
        );
        userRepository.save(created);
        UserAuthIdentityEntity identity = UserAuthIdentityEntity.create(
                created,
                "local",
                null,
                passwordHasher.hash(password),
                now,
                now
        );
        identityRepository.save(identity);
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
    public boolean deleteUser(Integer userId) {
        UserEntity target = userRepository.findById(userId).orElse(null);
        if (target == null) {
            return false;
        }
        deviceFacade.unassignDevicesForUser(userId);
        identityRepository.deleteAllByUser_Id(userId);
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
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public record UserProfile(
            Integer id,
            String email,
            String username,
            String role,
            boolean active,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record AuthUser(Integer id, String role, boolean active) {
    }
}
