package ru.growerhub.backend.user.engine;

import java.util.Optional;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;

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



