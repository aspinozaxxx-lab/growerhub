package ru.growerhub.backend.plant.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.growerhub.backend.plant.jpa.PlantGroupEntity;

public interface PlantGroupRepository extends JpaRepository<PlantGroupEntity, Integer> {
    List<PlantGroupEntity> findAllByUser_Id(Integer userId);

    Optional<PlantGroupEntity> findByIdAndUser_Id(Integer id, Integer userId);
}

