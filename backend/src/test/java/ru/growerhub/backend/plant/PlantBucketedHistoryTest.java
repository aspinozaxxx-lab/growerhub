package ru.growerhub.backend.plant;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.plant.contract.PlantMetricBucketPoint;
import ru.growerhub.backend.plant.contract.PlantMetricType;
import ru.growerhub.backend.plant.jpa.PlantEntity;
import ru.growerhub.backend.plant.jpa.PlantMetricSampleEntity;
import ru.growerhub.backend.plant.jpa.PlantMetricSampleRepository;
import ru.growerhub.backend.plant.jpa.PlantRepository;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;

@SpringBootTest
class PlantBucketedHistoryTest extends IntegrationTestBase {

    @Autowired
    private PlantFacade plantFacade;

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private PlantMetricSampleRepository sampleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        clearDatabase();
    }

    @Test
    void bucketedHistoryReturnsPointsPerMetric() {
        UserEntity owner = createUser("bucket-owner@example.com");
        PlantEntity plant = createPlant(owner, "Bucketed");

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime since = now.minusHours(6);

        insertSample(plant, PlantMetricType.SOIL_MOISTURE, since.plusMinutes(30), 40.0);
        insertSample(plant, PlantMetricType.SOIL_MOISTURE, since.plusMinutes(90), 45.0);
        insertSample(plant, PlantMetricType.SOIL_MOISTURE, since.plusHours(2).plusMinutes(15), 48.0);

        insertSample(plant, PlantMetricType.AIR_TEMPERATURE, since.plusHours(2).plusMinutes(10), 24.0);
        insertSample(plant, PlantMetricType.AIR_TEMPERATURE, since.plusHours(4).plusMinutes(5), 23.0);

        List<PlantMetricBucketPoint> points = plantFacade.getBucketedHistory(
                plant.getId(),
                new AuthenticatedUser(owner.getId(), "user"),
                List.of(PlantMetricType.SOIL_MOISTURE, PlantMetricType.AIR_TEMPERATURE),
                since,
                Duration.ofHours(2)
        );

        Map<String, List<PlantMetricBucketPoint>> byMetric = points.stream()
                .collect(Collectors.groupingBy(PlantMetricBucketPoint::metricType));

        Assertions.assertEquals(2, byMetric.size());
        Assertions.assertEquals(3, byMetric.get("SOIL_MOISTURE").size());
        Assertions.assertEquals(3, byMetric.get("AIR_TEMPERATURE").size());

        List<PlantMetricBucketPoint> soilPoints = byMetric.get("SOIL_MOISTURE");
        Assertions.assertEquals(45.0, soilPoints.get(0).value());
    }

    private UserEntity createUser(String email) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserEntity user = UserEntity.create(email, null, "user", true, now, now);
        return userRepository.save(user);
    }

    private PlantEntity createPlant(UserEntity owner, String name) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        PlantEntity plant = PlantEntity.create();
        plant.setName(name);
        plant.setUserId(owner.getId());
        plant.setPlantedAt(now.minusDays(5));
        plant.setCreatedAt(now);
        plant.setUpdatedAt(now);
        return plantRepository.save(plant);
    }

    private void insertSample(PlantEntity plant, PlantMetricType type, LocalDateTime ts, Double value) {
        PlantMetricSampleEntity sample = PlantMetricSampleEntity.create();
        sample.setPlant(plant);
        sample.setMetricType(type);
        sample.setTs(ts);
        sample.setValueNumeric(value);
        sample.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        sampleRepository.save(sample);
    }

    private void clearDatabase() {
        jdbcTemplate.update("DELETE FROM plant_metric_samples");
        jdbcTemplate.update("DELETE FROM plants");
        jdbcTemplate.update("DELETE FROM users");
    }
}
