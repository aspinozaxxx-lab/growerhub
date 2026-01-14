package ru.growerhub.backend.advisor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.advisor.contract.WateringAdviceBundle;
import ru.growerhub.backend.advisor.contract.WateringAdviceGateway;
import ru.growerhub.backend.advisor.jpa.AdvisorWateringAdviceEntity;
import ru.growerhub.backend.advisor.jpa.AdvisorWateringAdviceRepository;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.journal.jpa.PlantJournalEntryEntity;
import ru.growerhub.backend.journal.jpa.PlantJournalEntryRepository;
import ru.growerhub.backend.journal.jpa.PlantJournalWateringDetailsEntity;
import ru.growerhub.backend.journal.jpa.PlantJournalWateringDetailsRepository;
import ru.growerhub.backend.plant.jpa.PlantEntity;
import ru.growerhub.backend.plant.jpa.PlantRepository;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;

@SpringBootTest(properties = {
        "advisor.enabled=true",
        "advisor.watering.ttl=PT24H"
})
class AdvisorWateringCacheTest extends IntegrationTestBase {

    @Autowired
    private AdvisorFacade advisorFacade;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private PlantJournalEntryRepository entryRepository;

    @Autowired
    private PlantJournalWateringDetailsRepository detailsRepository;

    @Autowired
    private AdvisorWateringAdviceRepository adviceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FakeWateringGateway fakeGateway;

    @BeforeEach
    void setUp() {
        clearDatabase();
        fakeGateway.reset();
    }

    @Test
    void cacheHitSkipsLlm() {
        UserEntity owner = createUser("cache-owner@example.com");
        PlantEntity plant = createPlant(owner, "Basil");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime eventAt = now.minusHours(3);
        createWateringEntry(owner, plant, eventAt);

        AdvisorWateringAdviceEntity cached = AdvisorWateringAdviceEntity.create();
        cached.setPlantId(plant.getId());
        cached.setIsDue(true);
        cached.setRecommendedWaterVolumeL(1.2);
        cached.setRecommendedPh(6.4);
        cached.setRecommendedFertilizersPerLiter("g1-m2-b3 drops per liter");
        cached.setCreatedAt(now.minusHours(2));
        cached.setValidUntil(now.plusHours(2));
        adviceRepository.save(cached);

        fakeGateway.setResponse("{\"is_due\":false,\"recommended_water_volume_l\":0.5,\"recommended_ph\":6.1,\"recommended_fertilizers_per_liter\":\"g0-m0-b0\"}");

        WateringAdviceBundle result = advisorFacade.getWateringAdvice(plant.getId(), new AuthenticatedUser(owner.getId(), "user"));

        Assertions.assertEquals(0, fakeGateway.getCalls());
        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.advice());
        Assertions.assertEquals(1.2, result.advice().recommendedWaterVolumeL());
        Assertions.assertEquals(6.4, result.advice().recommendedPh());
    }

    @Test
    void wateringAfterCreatedAtRefreshesCache() {
        UserEntity owner = createUser("refresh-owner@example.com");
        PlantEntity plant = createPlant(owner, "Mint");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        AdvisorWateringAdviceEntity cached = AdvisorWateringAdviceEntity.create();
        cached.setPlantId(plant.getId());
        cached.setIsDue(false);
        cached.setRecommendedWaterVolumeL(0.4);
        cached.setRecommendedPh(6.0);
        cached.setRecommendedFertilizersPerLiter("g0-m0-b0 drops per liter");
        cached.setCreatedAt(now.minusHours(5));
        cached.setValidUntil(now.plusHours(5));
        adviceRepository.save(cached);

        createWateringEntry(owner, plant, now.minusHours(1));

        fakeGateway.setResponse("{\"is_due\":true,\"recommended_water_volume_l\":1.5,\"recommended_ph\":6.6,\"recommended_fertilizers_per_liter\":\"g2-m1-b3 drops per liter\"}");

        WateringAdviceBundle result = advisorFacade.getWateringAdvice(plant.getId(), new AuthenticatedUser(owner.getId(), "user"));

        Assertions.assertEquals(1, fakeGateway.getCalls());
        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.advice());
        Assertions.assertEquals(1.5, result.advice().recommendedWaterVolumeL());

        AdvisorWateringAdviceEntity updated = adviceRepository.findByPlantId(plant.getId()).orElse(null);
        Assertions.assertNotNull(updated);
        Assertions.assertTrue(updated.getCreatedAt().isAfter(cached.getCreatedAt()));
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
        plant.setPlantedAt(now.minusDays(10));
        plant.setCreatedAt(now);
        plant.setUpdatedAt(now);
        return plantRepository.save(plant);
    }

    private void createWateringEntry(UserEntity owner, PlantEntity plant, LocalDateTime eventAt) {
        PlantJournalEntryEntity entry = PlantJournalEntryEntity.create();
        entry.setPlantId(plant.getId());
        entry.setUserId(owner.getId());
        entry.setType("watering");
        entry.setText("test");
        entry.setEventAt(eventAt);
        entry.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        entry.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        entryRepository.save(entry);

        PlantJournalWateringDetailsEntity details = PlantJournalWateringDetailsEntity.create();
        details.setJournalEntry(entry);
        details.setWaterVolumeL(1.0);
        details.setDurationS(120);
        details.setPh(6.2);
        details.setFertilizersPerLiter("g1-m1-b1 drops per liter");
        detailsRepository.save(details);
    }

    private void clearDatabase() {
        jdbcTemplate.update("DELETE FROM advisor_watering_advice");
        jdbcTemplate.update("DELETE FROM plant_journal_watering_details");
        jdbcTemplate.update("DELETE FROM plant_journal_entries");
        jdbcTemplate.update("DELETE FROM plant_metric_samples");
        jdbcTemplate.update("DELETE FROM plants");
        jdbcTemplate.update("DELETE FROM users");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        FakeWateringGateway fakeWateringGateway() {
            return new FakeWateringGateway();
        }
    }

    static class FakeWateringGateway implements WateringAdviceGateway {
        private final AtomicInteger calls = new AtomicInteger();
        private String response;

        @Override
        public String requestWateringAdvice(String prompt) {
            calls.incrementAndGet();
            return response;
        }

        void setResponse(String response) {
            this.response = response;
        }

        int getCalls() {
            return calls.get();
        }

        void reset() {
            calls.set(0);
            response = null;
        }
    }
}
