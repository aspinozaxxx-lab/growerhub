package ru.growerhub.backend.device.jpa;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.growerhub.backend.device.jpa.MqttAckEntity;

public interface MqttAckRepository extends JpaRepository<MqttAckEntity, Integer> {
    Optional<MqttAckEntity> findByCorrelationId(String correlationId);

    @Modifying
    @Query("delete from MqttAckEntity ack where ack.expiresAt is not null and ack.expiresAt <= :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
