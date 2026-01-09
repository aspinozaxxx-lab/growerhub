package ru.growerhub.backend.plant.internal;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.growerhub.backend.plant.PlantMetricSampleEntity;
import ru.growerhub.backend.plant.PlantMetricType;

public interface PlantMetricSampleRepository extends JpaRepository<PlantMetricSampleEntity, Integer> {
    List<PlantMetricSampleEntity> findAllByPlant_IdAndMetricTypeInAndTsGreaterThanEqualOrderByTs(
            Integer plantId,
            List<PlantMetricType> metricTypes,
            LocalDateTime ts
    );
}
