package ru.growerhub.backend.db;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAuthIdentityRepository extends JpaRepository<UserAuthIdentityEntity, Integer> {
    List<UserAuthIdentityEntity> findAllByUser_Id(Integer userId);

    void deleteAllByUser_Id(Integer userId);

    Optional<UserAuthIdentityEntity> findByUser_IdAndProvider(Integer userId, String provider);

    Optional<UserAuthIdentityEntity> findByProviderAndProviderSubject(String provider, String providerSubject);
}
