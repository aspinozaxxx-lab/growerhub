package ru.growerhub.backend.user;

import java.util.Optional;
import org.springframework.stereotype.Service;

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
