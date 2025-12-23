package ru.growerhub.backend.db;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantRepository extends JpaRepository<PlantEntity, Integer> {
    List<PlantEntity> findAllByUser_Id(Integer userId);

    List<PlantEntity> findAllByUser_IdAndPlantGroup_Id(Integer userId, Integer plantGroupId);

    Optional<PlantEntity> findByIdAndUser_Id(Integer id, Integer userId);
}
