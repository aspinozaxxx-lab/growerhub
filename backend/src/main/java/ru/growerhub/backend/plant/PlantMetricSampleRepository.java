package ru.growerhub.backend.plant;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlantMetricSampleRepository extends JpaRepository<PlantMetricSampleEntity, Integer> {
    List<PlantMetricSampleEntity> findAllByPlant_IdAndMetricTypeInAndTsGreaterThanEqualOrderByTs(
            Integer plantId,
            List<PlantMetricType> metricTypes,
            LocalDateTime ts
    );
}
