package ru.growerhub.backend.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import ru.growerhub.backend.api.dto.UserDtos;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.user.UserFacade;

@RestController
@Validated
public class UsersController {

    private final UserFacade userFacade;

    public UsersController(UserFacade userFacade) {
        this.userFacade = userFacade;
    }

    @GetMapping("/api/users")
    public List<UserResponse> listUsers(@AuthenticationPrincipal AuthenticatedUser user) {
        requireAdmin(user);
        return userFacade.listUsers().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/api/users/{user_id}")
    public UserResponse getUser(
            @PathVariable("user_id") Integer userId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        requireAdmin(user);
        UserFacade.UserProfile target = userFacade.getUser(userId);
        if (target == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Polzovatel' ne najden");
        }
        return toResponse(target);
    }

    @PostMapping("/api/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(
            @Valid @RequestBody UserDtos.UserCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        requireAdmin(user);
        UserFacade.UserProfile created = userFacade.createUser(
                request.email(),
                request.username(),
                request.role(),
                request.password()
        );
        return toResponse(created);
    }

    @PatchMapping("/api/users/{user_id}")
    public UserResponse updateUser(
            @PathVariable("user_id") Integer userId,
            @Valid @RequestBody UserDtos.UserUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        requireAdmin(user);
        UserFacade.UserProfile updated = userFacade.updateUser(
                userId,
                request.username(),
                request.role(),
                request.isActive()
        );
        if (updated == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Polzovatel' ne najden");
        }
        return toResponse(updated);
    }

    @DeleteMapping("/api/users/{user_id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable("user_id") Integer userId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        requireAdmin(user);
        boolean removed = userFacade.deleteUser(userId);
        if (!removed) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Polzovatel' ne najden");
        }
        return ResponseEntity.noContent().build();
    }

    private void requireAdmin(AuthenticatedUser user) {
        if (user == null || !user.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Nedostatochno prav");
        }
    }

    private UserResponse toResponse(UserFacade.UserProfile user) {
        return new UserResponse(
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
