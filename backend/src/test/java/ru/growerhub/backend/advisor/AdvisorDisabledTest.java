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
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.plant.jpa.PlantEntity;
import ru.growerhub.backend.plant.jpa.PlantRepository;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;

@SpringBootTest(properties = {
        "advisor.enabled=false"
})
class AdvisorDisabledTest extends IntegrationTestBase {

    @Autowired
    private AdvisorFacade advisorFacade;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlantRepository plantRepository;

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
    void disabledAdvisorSkipsLlm() {
        UserEntity owner = createUser("disabled-owner@example.com");
        PlantEntity plant = createPlant(owner, "Basil");

        WateringAdviceBundle result = advisorFacade.getWateringAdvice(
                plant.getId(),
                new AuthenticatedUser(owner.getId(), "user")
        );

        Assertions.assertNull(result);
        Assertions.assertEquals(0, fakeGateway.getCalls());
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

        @Override
        public String requestWateringAdvice(String prompt) {
            calls.incrementAndGet();
            return "{}";
        }

        int getCalls() {
            return calls.get();
        }

        void reset() {
            calls.set(0);
        }
    }
}