package ru.growerhub.backend.device;

import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.device.engine.AckCleanupWorker;
import ru.growerhub.backend.device.jpa.MqttAckEntity;
import ru.growerhub.backend.device.jpa.MqttAckRepository;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"MQTT_HOST=", "ACK_TTL_SECONDS=60"}
)
class AckCleanupWorkerIntegrationTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MqttAckRepository mqttAckRepository;

    @Autowired
    private AckCleanupWorker ackCleanupWorker;

    @Autowired
    private Clock clock;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM mqtt_ack");
    }

    @Test
    void cleanupExpiredDeletesRows() {
        LocalDateTime now = LocalDateTime.now(clock);
        saveAck("corr-expired", now.minusSeconds(120), now.minusSeconds(60));
        saveAck("corr-active", now.minusSeconds(10), now.plusSeconds(60));

        Assertions.assertEquals(2, mqttAckRepository.count());

        ackCleanupWorker.cleanupExpired();

        Assertions.assertEquals(1, mqttAckRepository.count());
        Assertions.assertTrue(mqttAckRepository.findByCorrelationId("corr-active").isPresent());
        Assertions.assertTrue(mqttAckRepository.findByCorrelationId("corr-expired").isEmpty());
    }

    private void saveAck(String correlationId, LocalDateTime receivedAt, LocalDateTime expiresAt) {
        MqttAckEntity ack = MqttAckEntity.create();
        ack.setCorrelationId(correlationId);
        ack.setDeviceId("device-1");
        ack.setResult("accepted");
        ack.setStatus("ok");
        ack.setPayloadJson("{\"result\":\"accepted\"}");
        ack.setReceivedAt(receivedAt);
        ack.setExpiresAt(expiresAt);
        mqttAckRepository.save(ack);
    }
}
