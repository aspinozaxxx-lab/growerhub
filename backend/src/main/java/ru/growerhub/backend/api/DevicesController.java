package ru.growerhub.backend.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.CommonDtos;
import ru.growerhub.backend.api.dto.DeviceDtos;
import ru.growerhub.backend.db.DeviceEntity;
import ru.growerhub.backend.db.DeviceRepository;
import ru.growerhub.backend.db.DeviceStateLastEntity;
import ru.growerhub.backend.db.DeviceStateLastRepository;
import ru.growerhub.backend.db.PlantDeviceEntity;
import ru.growerhub.backend.db.PlantDeviceRepository;
import ru.growerhub.backend.db.SensorDataRepository;
import ru.growerhub.backend.device.DeviceService;
import ru.growerhub.backend.mqtt.model.DeviceState;
import ru.growerhub.backend.user.UserEntity;
import ru.growerhub.backend.user.UserRepository;

@RestController
@Validated
public class DevicesController {
    private final DeviceRepository deviceRepository;
    private final DeviceService deviceService;
    private final SensorDataRepository sensorDataRepository;
    private final DeviceStateLastRepository deviceStateLastRepository;
    private final PlantDeviceRepository plantDeviceRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public DevicesController(
            DeviceRepository deviceRepository,
            DeviceService deviceService,
            SensorDataRepository sensorDataRepository,
            DeviceStateLastRepository deviceStateLastRepository,
            PlantDeviceRepository plantDeviceRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this.deviceRepository = deviceRepository;
        this.deviceService = deviceService;
        this.sensorDataRepository = sensorDataRepository;
        this.deviceStateLastRepository = deviceStateLastRepository;
        this.plantDeviceRepository = plantDeviceRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/device/{device_id}/status")
    @Transactional
    public CommonDtos.MessageResponse updateDeviceStatus(
            @PathVariable("device_id") String deviceId,
            @Valid @RequestBody DeviceDtos.DeviceStatusRequest request
    ) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        DeviceState.RelayState light = new DeviceState.RelayState(request.isLightOn() ? "on" : "off");
        DeviceState.RelayState pump = new DeviceState.RelayState(request.isWatering() ? "on" : "off");
        DeviceState state = new DeviceState(
                null,
                null,
                request.soilMoisture(),
                request.airTemperature(),
                request.airHumidity(),
                null,
                null,
                light,
                pump,
                null
        );
        deviceService.handleState(deviceId, state, now);

        return new CommonDtos.MessageResponse("Status updated");
    }

    @GetMapping("/api/device/{device_id}/settings")
    @Transactional
    public DeviceDtos.DeviceSettingsResponse getDeviceSettings(
            @PathVariable("device_id") String deviceId
    ) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        DeviceEntity device = deviceService.ensureDeviceExists(deviceId, now);
        if (device == null) {
            device = DeviceEntity.create();
            device.setDeviceId(deviceId);
            device.setName(deviceService.defaultName(deviceId));
            device.setLastSeen(now);
            deviceService.applyDefaults(device, deviceId);
            deviceRepository.save(device);
        }

        return new DeviceDtos.DeviceSettingsResponse(
                defaultDouble(device.getTargetMoisture(), deviceService.defaultTargetMoisture()),
                defaultInteger(device.getWateringDuration(), deviceService.defaultWateringDuration()),
                defaultInteger(device.getWateringTimeout(), deviceService.defaultWateringTimeout()),
                device.getWateringSpeedLph(),
                defaultInteger(device.getLightOnHour(), deviceService.defaultLightOnHour()),
                defaultInteger(device.getLightOffHour(), deviceService.defaultLightOffHour()),
                defaultInteger(device.getLightDuration(), deviceService.defaultLightDuration()),
                defaultBoolean(device.getUpdateAvailable(), deviceService.defaultUpdateAvailable())
        );
    }

    @PutMapping("/api/device/{device_id}/settings")
    @Transactional
    public CommonDtos.MessageResponse updateDeviceSettings(
            @PathVariable("device_id") String deviceId,
            @Valid @RequestBody DeviceDtos.DeviceSettingsRequest request
    ) {
        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Device not found");
        }

        device.setTargetMoisture(request.targetMoisture());
        device.setWateringDuration(request.wateringDuration());
        device.setWateringTimeout(request.wateringTimeout());
        if (request.wateringSpeedLph() != null) {
            device.setWateringSpeedLph(request.wateringSpeedLph());
        }
        device.setLightOnHour(request.lightOnHour());
        device.setLightOffHour(request.lightOffHour());
        device.setLightDuration(request.lightDuration());
        deviceRepository.save(device);

        return new CommonDtos.MessageResponse("Settings updated");
    }

    @GetMapping("/api/devices")
    public List<DeviceDtos.DeviceResponse> listDevices() {
        List<DeviceEntity> devices = deviceRepository.findAll();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Duration onlineWindow = Duration.ofMinutes(3);
        List<DeviceDtos.DeviceResponse> responses = new ArrayList<>();
        for (DeviceEntity device : devices) {
            responses.add(toDeviceResponse(device, now, onlineWindow));
        }
        return responses;
    }

    @GetMapping("/api/devices/my")
    public List<DeviceDtos.DeviceResponse> listMyDevices(@AuthenticationPrincipal UserEntity user) {
        return deviceService.listMyDevices(user.getId());
    }

    @GetMapping("/api/admin/devices")
    public List<DeviceDtos.AdminDeviceResponse> listAdminDevices(@AuthenticationPrincipal UserEntity user) {
        requireAdmin(user);
        List<DeviceEntity> devices = deviceRepository.findAll();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Duration onlineWindow = Duration.ofMinutes(3);
        List<DeviceDtos.AdminDeviceResponse> responses = new ArrayList<>();
        for (DeviceEntity device : devices) {
            DeviceDtos.DeviceResponse base = toDeviceResponse(device, now, onlineWindow);
            UserEntity owner = device.getUser();
            DeviceDtos.DeviceOwnerInfoResponse ownerPayload = owner != null
                    ? new DeviceDtos.DeviceOwnerInfoResponse(owner.getId(), owner.getEmail(), owner.getUsername())
                    : null;
            responses.add(new DeviceDtos.AdminDeviceResponse(
                    base.id(),
                    base.deviceId(),
                    base.name(),
                    base.isOnline(),
                    base.soilMoisture(),
                    base.airTemperature(),
                    base.airHumidity(),
                    base.isWatering(),
                    base.isLightOn(),
                    base.lastWatering(),
                    base.lastSeen(),
                    base.targetMoisture(),
                    base.wateringDuration(),
                    base.wateringTimeout(),
                    base.wateringSpeedLph(),
                    base.lightOnHour(),
                    base.lightOffHour(),
                    base.lightDuration(),
                    base.currentVersion(),
                    base.updateAvailable(),
                    base.firmwareVersion(),
                    base.userId(),
                    base.plantIds(),
                    ownerPayload
            ));
        }
        return responses;
    }

    @PostMapping("/api/devices/assign-to-me")
    @Transactional
    public DeviceDtos.DeviceResponse assignToMe(
            @Valid @RequestBody DeviceDtos.AssignToMeRequest request,
            @AuthenticationPrincipal UserEntity user
    ) {
        DeviceEntity device = deviceRepository.findById(request.deviceId()).orElse(null);
        if (device == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ustrojstvo ne najdeno");
        }

        UserEntity owner = device.getUser();
        if (owner == null) {
            device.setUser(user);
        } else if (!owner.getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ustrojstvo uzhe privyazano k drugomu polzovatelju");
        }

        deviceRepository.save(device);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Duration onlineWindow = Duration.ofMinutes(3);
        return toDeviceResponse(device, now, onlineWindow);
    }

    @PostMapping("/api/devices/{device_id}/unassign")
    @Transactional
    public DeviceDtos.DeviceResponse unassignDevice(
            @PathVariable("device_id") Integer deviceId,
            @AuthenticationPrincipal UserEntity user
    ) {
        DeviceEntity device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ustrojstvo ne najdeno");
        }

        UserEntity owner = device.getUser();
        if (!"admin".equals(user.getRole()) && (owner == null || !owner.getId().equals(user.getId()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "nedostatochno prav dlya otvyazki etogo ustrojstva");
        }

        device.setUser(null);
        deviceRepository.save(device);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Duration onlineWindow = Duration.ofMinutes(3);
        return toDeviceResponse(device, now, onlineWindow);
    }

    @PostMapping("/api/admin/devices/{device_id}/assign")
    @Transactional
    public DeviceDtos.AdminDeviceResponse adminAssignDevice(
            @PathVariable("device_id") Integer deviceId,
            @Valid @RequestBody DeviceDtos.AdminAssignRequest request,
            @AuthenticationPrincipal UserEntity user
    ) {
        requireAdmin(user);
        DeviceEntity device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ustrojstvo ne najdeno");
        }

        UserEntity owner = userRepository.findById(request.userId()).orElse(null);
        if (owner == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "polzovatel' ne najden");
        }

        device.setUser(owner);
        deviceRepository.save(device);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Duration onlineWindow = Duration.ofMinutes(3);
        DeviceDtos.DeviceResponse base = toDeviceResponse(device, now, onlineWindow);
        DeviceDtos.DeviceOwnerInfoResponse ownerPayload = new DeviceDtos.DeviceOwnerInfoResponse(
                owner.getId(),
                owner.getEmail(),
                owner.getUsername()
        );
        return new DeviceDtos.AdminDeviceResponse(
                base.id(),
                base.deviceId(),
                base.name(),
                base.isOnline(),
                base.soilMoisture(),
                base.airTemperature(),
                base.airHumidity(),
                base.isWatering(),
                base.isLightOn(),
                base.lastWatering(),
                base.lastSeen(),
                base.targetMoisture(),
                base.wateringDuration(),
                base.wateringTimeout(),
                base.wateringSpeedLph(),
                base.lightOnHour(),
                base.lightOffHour(),
                base.lightDuration(),
                base.currentVersion(),
                base.updateAvailable(),
                base.firmwareVersion(),
                base.userId(),
                base.plantIds(),
                ownerPayload
        );
    }

    @PostMapping("/api/admin/devices/{device_id}/unassign")
    @Transactional
    public DeviceDtos.AdminDeviceResponse adminUnassignDevice(
            @PathVariable("device_id") Integer deviceId,
            @AuthenticationPrincipal UserEntity user
    ) {
        requireAdmin(user);
        DeviceEntity device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ustrojstvo ne najdeno");
        }

        device.setUser(null);
        deviceRepository.save(device);

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Duration onlineWindow = Duration.ofMinutes(3);
        DeviceDtos.DeviceResponse base = toDeviceResponse(device, now, onlineWindow);
        return new DeviceDtos.AdminDeviceResponse(
                base.id(),
                base.deviceId(),
                base.name(),
                base.isOnline(),
                base.soilMoisture(),
                base.airTemperature(),
                base.airHumidity(),
                base.isWatering(),
                base.isLightOn(),
                base.lastWatering(),
                base.lastSeen(),
                base.targetMoisture(),
                base.wateringDuration(),
                base.wateringTimeout(),
                base.wateringSpeedLph(),
                base.lightOnHour(),
                base.lightOffHour(),
                base.lightDuration(),
                base.currentVersion(),
                base.updateAvailable(),
                base.firmwareVersion(),
                base.userId(),
                base.plantIds(),
                null
        );
    }

    @DeleteMapping("/api/device/{device_id}")
    @Transactional
    public CommonDtos.MessageResponse deleteDevice(
            @PathVariable("device_id") String deviceId
    ) {
        DeviceEntity device = deviceRepository.findByDeviceId(deviceId).orElse(null);
        if (device == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Device not found");
        }

        sensorDataRepository.deleteAllByDeviceId(deviceId);
        deviceRepository.delete(device);
        return new CommonDtos.MessageResponse("Device deleted");
    }

    private void requireAdmin(UserEntity user) {
        if (user == null || !"admin".equals(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Nedostatochno prav");
        }
    }

    private DeviceDtos.DeviceResponse toDeviceResponse(
            DeviceEntity device,
            LocalDateTime now,
            Duration onlineWindow
    ) {
        DeviceStateSnapshot snapshot = resolveState(device, now, onlineWindow);
        List<PlantDeviceEntity> links = plantDeviceRepository.findAllByDevice_Id(device.getId());
        List<Integer> plantIds = new ArrayList<>();
        for (PlantDeviceEntity link : links) {
            if (link.getPlant() != null) {
                plantIds.add(link.getPlant().getId());
            }
        }

        UserEntity owner = device.getUser();
        Integer ownerId = owner != null ? owner.getId() : null;
        return new DeviceDtos.DeviceResponse(
                device.getId(),
                device.getDeviceId(),
                defaultString(device.getName(), deviceService.defaultName(device.getDeviceId())),
                snapshot.isOnline(),
                defaultDouble(device.getSoilMoisture(), deviceService.defaultSoilMoisture()),
                defaultDouble(device.getAirTemperature(), deviceService.defaultAirTemperature()),
                defaultDouble(device.getAirHumidity(), deviceService.defaultAirHumidity()),
                defaultBoolean(snapshot.isWatering(), deviceService.defaultIsWatering()),
                defaultBoolean(device.getIsLightOn(), deviceService.defaultIsLightOn()),
                device.getLastWatering(),
                snapshot.lastSeen(),
                defaultDouble(device.getTargetMoisture(), deviceService.defaultTargetMoisture()),
                defaultInteger(device.getWateringDuration(), deviceService.defaultWateringDuration()),
                defaultInteger(device.getWateringTimeout(), deviceService.defaultWateringTimeout()),
                device.getWateringSpeedLph(),
                defaultInteger(device.getLightOnHour(), deviceService.defaultLightOnHour()),
                defaultInteger(device.getLightOffHour(), deviceService.defaultLightOffHour()),
                defaultInteger(device.getLightDuration(), deviceService.defaultLightDuration()),
                defaultString(device.getCurrentVersion(), deviceService.defaultCurrentVersion()),
                defaultBoolean(device.getUpdateAvailable(), deviceService.defaultUpdateAvailable()),
                snapshot.firmwareVersion(),
                ownerId,
                plantIds
        );
    }

    private DeviceStateSnapshot resolveState(DeviceEntity device, LocalDateTime now, Duration onlineWindow) {
        String firmwareVersion = "old";
        Boolean isWatering = device.getIsWatering();
        LocalDateTime lastSeen = device.getLastSeen();
        boolean isOnline = lastSeen != null && Duration.between(lastSeen, now).compareTo(onlineWindow) <= 0;

        DeviceStateLastEntity state = deviceStateLastRepository.findByDeviceId(device.getDeviceId()).orElse(null);
        if (state == null) {
            return new DeviceStateSnapshot(firmwareVersion, isWatering, lastSeen, isOnline);
        }

        Map<String, Object> payload = parseStateJson(state.getStateJson());
        if (payload != null) {
            Object fwVer = payload.get("fw_ver");
            if (fwVer != null && !fwVer.toString().isBlank()) {
                firmwareVersion = fwVer.toString();
            }
            Object manualObj = payload.get("manual_watering");
            if (manualObj instanceof Map<?, ?> manual) {
                Object statusObj = manual.get("status");
                if (statusObj != null) {
                    isWatering = "running".equals(statusObj.toString());
                }
            }
        }

        if (state.getUpdatedAt() != null) {
            lastSeen = state.getUpdatedAt();
            isOnline = Duration.between(lastSeen, now).compareTo(onlineWindow) <= 0;
        }

        return new DeviceStateSnapshot(firmwareVersion, isWatering, lastSeen, isOnline);
    }

    private Map<String, Object> parseStateJson(String stateJson) {
        if (stateJson == null) {
            return null;
        }
        try {
            return objectMapper.readValue(stateJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return null;
        }
    }

    private Double defaultDouble(Double value, double fallback) {
        return value != null ? value : fallback;
    }

    private Integer defaultInteger(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    private Boolean defaultBoolean(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }

    private String defaultString(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private record DeviceStateSnapshot(String firmwareVersion, Boolean isWatering, LocalDateTime lastSeen, boolean isOnline) {
    }
}
