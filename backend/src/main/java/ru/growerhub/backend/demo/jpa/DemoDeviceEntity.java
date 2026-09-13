package ru.growerhub.backend.demo.jpa;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "demo_devices")
public class DemoDeviceEntity {
    @Id public UUID id;
    @Column(name = "space_id", nullable = false) public UUID spaceId;
    @Column(name = "profile_key", nullable = false) public String profileKey;
    @Column(name = "target_id", nullable = false, unique = true) public String targetId;
    @Column(name = "native_device_id") public Integer nativeDeviceId;
    @Column(name = "coordinator_id") public Integer coordinatorId;
    @Column(name = "state_json", nullable = false, columnDefinition = "text") public String stateJson;
    @Column(name = "updated_at", nullable = false) public LocalDateTime updatedAt;
    @Column(name = "stop_at") public LocalDateTime stopAt;
}
