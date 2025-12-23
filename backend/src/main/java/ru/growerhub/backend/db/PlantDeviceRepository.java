package ru.growerhub.backend.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantDeviceRepository extends JpaRepository<PlantDeviceEntity, Integer> {
    List<PlantDeviceEntity> findAllByDevice_Id(Integer deviceId);
}
