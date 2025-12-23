package ru.growerhub.backend.api;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.auth.PasswordHasher;
import ru.growerhub.backend.db.DeviceEntity;
import ru.growerhub.backend.db.DeviceRepository;
import ru.growerhub.backend.db.UserAuthIdentityEntity;
import ru.growerhub.backend.db.UserAuthIdentityRepository;
import ru.growerhub.backend.api.dto.UserDtos;
import jakarta.validation.Valid;
import ru.growerhub.backend.user.UserEntity;
import ru.growerhub.backend.user.UserRepository;

@RestController
@Validated
public class UsersController {

    private final UserRepository userRepository;
    private final UserAuthIdentityRepository identityRepository;
    private final DeviceRepository deviceRepository;
    private final PasswordHasher passwordHasher;

    public UsersController(
            UserRepository userRepository,
            UserAuthIdentityRepository identityRepository,
            DeviceRepository deviceRepository,
            PasswordHasher passwordHasher
    ) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.deviceRepository = deviceRepository;
        this.passwordHasher = passwordHasher;
    }

    @GetMapping("/api/users")
    public List<UserResponse> listUsers(@AuthenticationPrincipal UserEntity user) {
        requireAdmin(user);
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/api/users/{user_id}")
    public UserResponse getUser(
            @PathVariable("user_id") Integer userId,
            @AuthenticationPrincipal UserEntity user
    ) {
        requireAdmin(user);
        UserEntity target = userRepository.findById(userId).orElse(null);
        if (target == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Polzovatel' ne najden");
        }
        return toResponse(target);
    }

    @PostMapping("/api/users")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UserResponse createUser(
            @Valid @RequestBody UserDtos.UserCreateRequest request,
            @AuthenticationPrincipal UserEntity user
    ) {
        requireAdmin(user);
        UserEntity existing = userRepository.findByEmail(request.email()).orElse(null);
        if (existing != null) {
            throw new ApiException(HttpStatus.CONFLICT, "Polzovatel' s takim email uzhe sushhestvuet");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String role = request.role() == null || request.role().isEmpty() ? "user" : request.role();
        UserEntity created = UserEntity.create(
                request.email(),
                request.username(),
                role,
                true,
                now,
                now
        );
        userRepository.save(created);
        UserAuthIdentityEntity identity = UserAuthIdentityEntity.create(
                created,
                "local",
                null,
                passwordHasher.hash(request.password()),
                now,
                now
        );
        identityRepository.save(identity);
        return toResponse(created);
    }

    @PatchMapping("/api/users/{user_id}")
    @Transactional
    public UserResponse updateUser(
            @PathVariable("user_id") Integer userId,
            @Valid @RequestBody UserDtos.UserUpdateRequest request,
            @AuthenticationPrincipal UserEntity user
    ) {
        requireAdmin(user);
        UserEntity target = userRepository.findById(userId).orElse(null);
        if (target == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Polzovatel' ne najden");
        }
        boolean changed = false;
        if (request.username() != null) {
            target.setUsername(request.username());
            changed = true;
        }
        if (request.role() != null) {
            target.setRole(request.role());
            changed = true;
        }
        if (request.isActive() != null) {
            target.setActive(request.isActive());
            changed = true;
        }
        if (changed) {
            target.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        userRepository.save(target);
        return toResponse(target);
    }

    @DeleteMapping("/api/users/{user_id}")
    @Transactional
    public ResponseEntity<Void> deleteUser(
            @PathVariable("user_id") Integer userId,
            @AuthenticationPrincipal UserEntity user
    ) {
        requireAdmin(user);
        UserEntity target = userRepository.findById(userId).orElse(null);
        if (target == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Polzovatel' ne najden");
        }
        List<DeviceEntity> devices = deviceRepository.findAllByUser_Id(userId);
        for (DeviceEntity device : devices) {
            device.setUser(null);
        }
        deviceRepository.saveAll(devices);
        identityRepository.deleteAllByUser_Id(userId);
        userRepository.delete(target);
        return ResponseEntity.noContent().build();
    }

    private void requireAdmin(UserEntity user) {
        if (user == null || !"admin".equals(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Nedostatochno prav");
        }
    }

    private UserResponse toResponse(UserEntity user) {
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
