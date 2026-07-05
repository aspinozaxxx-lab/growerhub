package ru.growerhub.backend.zigbee.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ZigbeeDeviceStateEventRepository extends JpaRepository<ZigbeeDeviceStateEventEntity, Integer> {
}
