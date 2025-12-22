package ru.growerhub.backend.db;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAuthIdentityRepository extends JpaRepository<UserAuthIdentityEntity, Integer> {}
