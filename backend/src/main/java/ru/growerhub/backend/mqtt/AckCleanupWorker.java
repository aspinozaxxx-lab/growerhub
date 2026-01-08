package ru.growerhub.backend.mqtt;

import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AckCleanupWorker {
    private static final Logger logger = LoggerFactory.getLogger(AckCleanupWorker.class);

    private final MqttAckRepository mqttAckRepository;
    private final AckSettings ackSettings;
    private final Clock clock;

    public AckCleanupWorker(MqttAckRepository mqttAckRepository, AckSettings ackSettings, Clock clock) {
        this.mqttAckRepository = mqttAckRepository;
        this.ackSettings = ackSettings;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${ACK_CLEANUP_PERIOD_SECONDS:60}000")
    @Transactional
    public void cleanupExpired() {
        int ttlSeconds = ackSettings.getTtlSeconds();
        if (ttlSeconds <= 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int deleted = mqttAckRepository.deleteExpired(now);
        if (deleted > 0) {
            logger.info("Ack cleanup deleted {} rows", deleted);
        }
    }
}
