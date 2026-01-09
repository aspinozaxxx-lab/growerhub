package ru.growerhub.backend.user.internal;

import java.util.Optional;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.user.UserEntity;

@Service
public class UserService {
    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public Optional<UserEntity> findById(int id) {
        return repository.findById(id);
    }
}
