﻿package ru.growerhub.backend.auth.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAuthIdentityRepository extends JpaRepository<UserAuthIdentityEntity, Integer> {
    List<UserAuthIdentityEntity> findAllByUserId(Integer userId);

    void deleteAllByUserId(Integer userId);

    Optional<UserAuthIdentityEntity> findByUserIdAndProvider(Integer userId, String provider);

    Optional<UserAuthIdentityEntity> findByProviderAndProviderSubject(String provider, String providerSubject);
}
