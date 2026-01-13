package ru.growerhub.backend.plant.jpa;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.growerhub.backend.plant.contract.PlantMetricType;
import ru.growerhub.backend.plant.jpa.PlantMetricSampleEntity;

public interface PlantMetricSampleRepository extends JpaRepository<PlantMetricSampleEntity, Integer> {
    List<PlantMetricSampleEntity> findAllByPlant_IdAndMetricTypeInAndTsGreaterThanEqualOrderByTs(
            Integer plantId,
            List<PlantMetricType> metricTypes,
            LocalDateTime ts
    );
}

