package ru.growerhub.backend.api;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
import ru.growerhub.backend.device.DeviceEntity;
import ru.growerhub.backend.device.DeviceIngestionService;
import ru.growerhub.backend.device.DeviceQueryService;
import ru.growerhub.backend.device.DeviceRepository;
import ru.growerhub.backend.device.DeviceStateLastRepository;
import ru.growerhub.backend.mqtt.model.DeviceState;
import ru.growerhub.backend.user.UserEntity;
import ru.growerhub.backend.user.UserRepository;

@RestController
@Validated
public class DevicesController {
    private final DeviceRepository deviceRepository;
    private final DeviceIngestionService deviceIngestionService;
    private final DeviceQueryService deviceQueryService;
    private final DeviceStateLastRepository deviceStateLastRepository;
    private final UserRepository userRepository;

    public DevicesController(
            DeviceRepository deviceRepository,
            DeviceIngestionService deviceIngestionService,
            DeviceQueryService deviceQueryService,
            DeviceStateLastRepository deviceStateLastRepository,
            UserRepository userRepository
    ) {
        this.deviceRepository = deviceRepository;
        this.deviceIngestionService = deviceIngestionService;
        this.deviceQueryService = deviceQueryService;
        this.deviceStateLastRepository = deviceStateLastRepository;
        this.userRepository = userRepository;
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
        Integer soilPercent = request.soilMoisture() != null
                ? (int) Math.round(request.soilMoisture())
                : null;
        DeviceState.SoilPort soilPort = new DeviceState.SoilPort(0, true, soilPercent);
        DeviceState.SoilState soil = new DeviceState.SoilState(List.of(soilPort));
        DeviceState.AirState air = new DeviceState.AirState(true, request.airTemperature(), request.airHumidity());
        DeviceState state = new DeviceState(
                null,
                null,
                null,
                null,
                null,
                air,
                soil,
                light,
                pump,
                null
        );
        deviceIngestionService.handleState(deviceId, state, now);
        return new CommonDtos.MessageResponse("Status updated");
    }

    @GetMapping("/api/device/{device_id}/settings")
    @Transactional
    public DeviceDtos.DeviceSettingsResponse getDeviceSettings(
            @PathVariable("device_id") String deviceId
    ) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        DeviceEntity device = deviceIngestionService.ensureDeviceExists(deviceId, now);
        if (device == null) {
            device = DeviceEntity.create();
            device.setDeviceId(deviceId);
            device.setName(deviceIngestionService.defaultName(deviceId));
            device.setLastSeen(now);
            deviceIngestionService.applyDefaults(device, deviceId);
            deviceRepository.save(device);
        }

        return new DeviceDtos.DeviceSettingsResponse(
                defaultDouble(device.getTargetMoisture(), deviceIngestionService.defaultTargetMoisture()),
                defaultInteger(device.getWateringDuration(), deviceIngestionService.defaultWateringDuration()),
                defaultInteger(device.getWateringTimeout(), deviceIngestionService.defaultWateringTimeout()),
                defaultInteger(device.getLightOnHour(), deviceIngestionService.defaultLightOnHour()),
                defaultInteger(device.getLightOffHour(), deviceIngestionService.defaultLightOffHour()),
                defaultInteger(device.getLightDuration(), deviceIngestionService.defaultLightDuration()),
                defaultBoolean(device.getUpdateAvailable(), deviceIngestionService.defaultUpdateAvailable())
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
        device.setLightOnHour(request.lightOnHour());
        device.setLightOffHour(request.lightOffHour());
        device.setLightDuration(request.lightDuration());
        deviceRepository.save(device);

        return new CommonDtos.MessageResponse("Settings updated");
    }

    @GetMapping("/api/devices")
    public List<DeviceDtos.DeviceResponse> listDevices() {
        return deviceQueryService.listDevices();
    }

    @GetMapping("/api/devices/my")
    public List<DeviceDtos.DeviceResponse> listMyDevices(@AuthenticationPrincipal UserEntity user) {
        return deviceQueryService.listMyDevices(user.getId());
    }

    @GetMapping("/api/admin/devices")
    public List<DeviceDtos.AdminDeviceResponse> listAdminDevices(@AuthenticationPrincipal UserEntity user) {
        requireAdmin(user);
        return deviceQueryService.listAdminDevices();
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
        return deviceQueryService.buildDeviceResponse(device);
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
        return deviceQueryService.buildDeviceResponse(device);
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

        DeviceDtos.DeviceResponse base = deviceQueryService.buildDeviceResponse(device);
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
                base.lastSeen(),
                base.targetMoisture(),
                base.wateringDuration(),
                base.wateringTimeout(),
                base.lightOnHour(),
                base.lightOffHour(),
                base.lightDuration(),
                base.currentVersion(),
                base.updateAvailable(),
                base.firmwareVersion(),
                base.userId(),
                base.sensors(),
                base.pumps(),
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

        DeviceDtos.DeviceResponse base = deviceQueryService.buildDeviceResponse(device);
        return new DeviceDtos.AdminDeviceResponse(
                base.id(),
                base.deviceId(),
                base.name(),
                base.isOnline(),
                base.lastSeen(),
                base.targetMoisture(),
                base.wateringDuration(),
                base.wateringTimeout(),
                base.lightOnHour(),
                base.lightOffHour(),
                base.lightDuration(),
                base.currentVersion(),
                base.updateAvailable(),
                base.firmwareVersion(),
                base.userId(),
                base.sensors(),
                base.pumps(),
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

        deviceStateLastRepository.deleteByDeviceId(deviceId);
        deviceRepository.delete(device);
        return new CommonDtos.MessageResponse("Device deleted");
    }

    private void requireAdmin(UserEntity user) {
        if (user == null || !"admin".equals(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Nedostatochno prav");
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
}
