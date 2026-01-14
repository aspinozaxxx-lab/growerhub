package ru.growerhub.backend.advisor.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdvisorWateringAdviceRepository extends JpaRepository<AdvisorWateringAdviceEntity, Integer> {
    Optional<AdvisorWateringAdviceEntity> findByPlantId(Integer plantId);
}
