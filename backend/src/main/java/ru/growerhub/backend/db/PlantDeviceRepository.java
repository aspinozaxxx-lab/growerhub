package ru.growerhub.backend.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantDeviceRepository extends JpaRepository<PlantDeviceEntity, Integer> {
    List<PlantDeviceEntity> findAllByDevice_Id(Integer deviceId);

    List<PlantDeviceEntity> findAllByPlant_Id(Integer plantId);

    PlantDeviceEntity findByPlant_IdAndDevice_Id(Integer plantId, Integer deviceId);

    void deleteAllByPlant_Id(Integer plantId);

    void deleteByPlant_IdAndDevice_Id(Integer plantId, Integer deviceId);
}
