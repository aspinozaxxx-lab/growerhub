package ru.growerhub.backend.device.engine;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.device.contract.DeviceServiceEventData;
import ru.growerhub.backend.device.contract.DeviceServiceEventType;
import ru.growerhub.backend.device.contract.DeviceServiceEventView;
import ru.growerhub.backend.device.jpa.DeviceEntity;
import ru.growerhub.backend.device.jpa.DeviceRepository;
import ru.growerhub.backend.device.jpa.DeviceServiceEventEntity;
import ru.growerhub.backend.device.jpa.DeviceServiceEventRepository;

@Service
public class DeviceServiceEventService {
    private final DeviceRepository deviceRepository;
    private final DeviceIngestionService deviceIngestionService;
    private final DeviceServiceEventRepository eventRepository;

    public DeviceServiceEventService(
            DeviceRepository deviceRepository,
            DeviceIngestionService deviceIngestionService,
            DeviceServiceEventRepository eventRepository
    ) {
        this.deviceRepository = deviceRepository;
        this.deviceIngestionService = deviceIngestionService;
        this.eventRepository = eventRepository;
    }

    public void recordEvent(String deviceKey, DeviceServiceEventData data, LocalDateTime receivedAt) {
        DeviceServiceEventType eventType = DeviceServiceEventType.fromWire(data != null ? data.type() : null);
        if (eventType == null) {
            return;
        }
        DeviceEntity device = deviceIngestionService.ensureDeviceExists(deviceKey, receivedAt);
        if (device == null) {
            return;
        }
        device.setLastSeen(receivedAt);
        deviceRepository.save(device);

        DeviceServiceEventEntity entity = DeviceServiceEventEntity.create();
        entity.setDeviceId(device.getId());
        entity.setEventType(eventType);
        entity.setSensorScope(data.sensorScope());
        entity.setSensorType(data.sensorType());
        entity.setChannel(data.channel());
        entity.setFailureId(data.failureId());
        entity.setErrorCode(data.errorCode());
        entity.setEventAt(data.eventAt());
        entity.setReceivedAt(receivedAt);
        entity.setPayloadJson(data.payloadJson());
        eventRepository.save(entity);
    }

    public Map<Integer, List<DeviceServiceEventView>> listRecentByDeviceIds(List<Integer> deviceIds, int limitPerDevice) {
        Map<Integer, List<DeviceServiceEventView>> result = new LinkedHashMap<>();
        if (deviceIds == null || deviceIds.isEmpty()) {
            return result;
        }
        List<DeviceServiceEventEntity> rows = eventRepository.findAllByDeviceIdInOrderByReceivedAtDesc(deviceIds);
        for (Integer deviceId : deviceIds) {
            result.put(deviceId, new ArrayList<>());
        }
        for (DeviceServiceEventEntity row : rows) {
            List<DeviceServiceEventView> items = result.computeIfAbsent(row.getDeviceId(), key -> new ArrayList<>());
            if (items.size() >= limitPerDevice) {
                continue;
            }
            items.add(new DeviceServiceEventView(
                    row.getId(),
                    row.getDeviceId(),
                    row.getEventType(),
                    row.getSensorScope(),
                    row.getSensorType(),
                    row.getChannel(),
                    row.getFailureId(),
                    row.getErrorCode(),
                    row.getEventAt(),
                    row.getReceivedAt()
            ));
        }
        return result;
    }

    public void deleteByDeviceId(Integer deviceId) {
        if (deviceId == null) {
            return;
        }
        eventRepository.deleteAllByDeviceId(deviceId);
    }
}
