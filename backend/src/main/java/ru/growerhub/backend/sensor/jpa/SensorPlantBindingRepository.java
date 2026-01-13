package ru.growerhub.backend.sensor.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensorPlantBindingRepository extends JpaRepository<SensorPlantBindingEntity, Integer> {
    List<SensorPlantBindingEntity> findAllBySensor_Id(Integer sensorId);

    List<SensorPlantBindingEntity> findAllBySensor_IdIn(List<Integer> sensorIds);

    List<SensorPlantBindingEntity> findAllByPlantId(Integer plantId);

    void deleteAllBySensor_Id(Integer sensorId);
}


