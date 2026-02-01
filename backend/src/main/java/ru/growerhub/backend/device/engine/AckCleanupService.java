package ru.growerhub.backend.device.engine;

import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.config.AckSettings;
import ru.growerhub.backend.device.jpa.MqttAckRepository;

@Service
public class AckCleanupService {
    private static final Logger logger = LoggerFactory.getLogger(AckCleanupService.class);

    private final MqttAckRepository mqttAckRepository;
    private final AckSettings ackSettings;
    private final Clock clock;

    public AckCleanupService(MqttAckRepository mqttAckRepository, AckSettings ackSettings, Clock clock) {
        this.mqttAckRepository = mqttAckRepository;
        this.ackSettings = ackSettings;
        this.clock = clock;
    }

    // Translitem: ochistka ACK v tranzakcii, chtoby deleteExpired ne padal bez active tx.
    @Transactional
    public int cleanupExpired() {
        int ttlSeconds = ackSettings.getTtlSeconds();
        if (ttlSeconds <= 0) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int deleted = mqttAckRepository.deleteExpired(now);
        if (deleted > 0) {
            logger.info("Ack cleanup deleted {} rows", deleted);
        }
        return deleted;
    }
}
