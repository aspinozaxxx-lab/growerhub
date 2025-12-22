package ru.growerhub.backend.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.UserDtos;

@RestController
public class UsersController {

    @GetMapping("/api/users")
    public List<UserResponse> listUsers() {
        throw todo();
    }

    @GetMapping("/api/users/{user_id}")
    public UserResponse getUser(@PathVariable("user_id") Integer userId) {
        throw todo();
    }

    @PostMapping("/api/users")
    public UserResponse createUser(@RequestBody UserDtos.UserCreateRequest request) {
        throw todo();
    }

    @PatchMapping("/api/users/{user_id}")
    public UserResponse updateUser(
            @PathVariable("user_id") Integer userId,
            @RequestBody UserDtos.UserUpdateRequest request
    ) {
        throw todo();
    }

    @DeleteMapping("/api/users/{user_id}")
    public ResponseEntity<Void> deleteUser(@PathVariable("user_id") Integer userId) {
        throw todo();
    }

    private static ApiException todo() {
        return new ApiException(HttpStatus.NOT_IMPLEMENTED, "TODO");
    }
}
