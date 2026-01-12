package ru.growerhub.backend.api;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.device.DeviceFacade;
import ru.growerhub.backend.device.contract.DeviceAggregate;
import ru.growerhub.backend.device.contract.DeviceSettingsData;
import ru.growerhub.backend.device.contract.DeviceSettingsUpdate;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.device.contract.DeviceSummary;
import ru.growerhub.backend.pump.PumpFacade;
import ru.growerhub.backend.pump.contract.PumpView;
import ru.growerhub.backend.sensor.SensorFacade;
import ru.growerhub.backend.sensor.contract.SensorView;
import ru.growerhub.backend.user.UserFacade;

@RestController
@Validated
public class DevicesController {
    private final DeviceFacade deviceFacade;
    private final SensorFacade sensorFacade;
    private final PumpFacade pumpFacade;
    private final UserFacade userFacade;

    public DevicesController(
            DeviceFacade deviceFacade,
            SensorFacade sensorFacade,
            PumpFacade pumpFacade,
            UserFacade userFacade
    ) {
        this.deviceFacade = deviceFacade;
        this.sensorFacade = sensorFacade;
        this.pumpFacade = pumpFacade;
        this.userFacade = userFacade;
    }

    @PostMapping("/api/device/{device_id}/status")
    public CommonDtos.MessageResponse updateDeviceStatus(
            @PathVariable("device_id") String deviceId,
            @Valid @RequestBody DeviceDtos.DeviceStatusRequest request
    ) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        DeviceShadowState.RelayState light = new DeviceShadowState.RelayState(request.isLightOn() ? "on" : "off");
        DeviceShadowState.RelayState pump = new DeviceShadowState.RelayState(request.isWatering() ? "on" : "off");
        Integer soilPercent = request.soilMoisture() != null
                ? (int) Math.round(request.soilMoisture())
                : null;
        DeviceShadowState.SoilPort soilPort = new DeviceShadowState.SoilPort(0, true, soilPercent);
        DeviceShadowState.SoilState soil = new DeviceShadowState.SoilState(List.of(soilPort));
        DeviceShadowState.AirState air = new DeviceShadowState.AirState(true, request.airTemperature(), request.airHumidity());
        DeviceShadowState state = new DeviceShadowState(
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
        deviceFacade.handleState(deviceId, state, now);
        return new CommonDtos.MessageResponse("Status updated");
    }

    @GetMapping("/api/device/{device_id}/settings")
    public DeviceDtos.DeviceSettingsResponse getDeviceSettings(
            @PathVariable("device_id") String deviceId
    ) {
        DeviceSettingsData settings = deviceFacade.getSettings(deviceId);
        return new DeviceDtos.DeviceSettingsResponse(
                settings.targetMoisture(),
                settings.wateringDuration(),
                settings.wateringTimeout(),
                settings.lightOnHour(),
                settings.lightOffHour(),
                settings.lightDuration(),
                settings.updateAvailable()
        );
    }

    @PutMapping("/api/device/{device_id}/settings")
    public CommonDtos.MessageResponse updateDeviceSettings(
            @PathVariable("device_id") String deviceId,
            @Valid @RequestBody DeviceDtos.DeviceSettingsRequest request
    ) {
        deviceFacade.updateSettings(deviceId, new DeviceSettingsUpdate(
                request.targetMoisture(),
                request.wateringDuration(),
                request.wateringTimeout(),
                request.lightOnHour(),
                request.lightOffHour(),
                request.lightDuration()
        ));
        return new CommonDtos.MessageResponse("Settings updated");
    }

    @GetMapping("/api/devices")
    public List<DeviceDtos.DeviceResponse> listDevices() {
        return mapDeviceResponses(deviceFacade.listDevices());
    }

    @GetMapping("/api/devices/my")
    public List<DeviceDtos.DeviceResponse> listMyDevices(@AuthenticationPrincipal AuthenticatedUser user) {
        return mapDeviceResponses(deviceFacade.listMyDevices(user != null ? user.id() : null));
    }

    @GetMapping("/api/admin/devices")
    public List<DeviceDtos.AdminDeviceResponse> listAdminDevices(@AuthenticationPrincipal AuthenticatedUser user) {
        requireAdmin(user);
        List<DeviceSummary> devices = deviceFacade.listAdminDevices();
        List<DeviceDtos.AdminDeviceResponse> responses = new ArrayList<>();
        for (DeviceSummary summary : devices) {
            DeviceDtos.DeviceResponse base = mapDeviceResponse(summary);
            UserFacade.UserProfile owner = summary.userId() != null ? userFacade.getUser(summary.userId()) : null;
            DeviceDtos.DeviceOwnerInfoResponse ownerPayload = owner != null
                    ? new DeviceDtos.DeviceOwnerInfoResponse(owner.id(), owner.email(), owner.username())
                    : null;
            responses.add(new DeviceDtos.AdminDeviceResponse(
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
            ));
        }
        return responses;
    }

    @PostMapping("/api/devices/assign-to-me")
    public DeviceDtos.DeviceResponse assignToMe(
            @Valid @RequestBody DeviceDtos.AssignToMeRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        DeviceAggregate aggregate = deviceFacade.assignToUserAggregate(
                request.deviceId(),
                user != null ? user.id() : null
        );
        return toDeviceResponse(aggregate);
    }

    @PostMapping("/api/devices/{device_id}/unassign")
    public DeviceDtos.DeviceResponse unassignDevice(
            @PathVariable("device_id") Integer deviceId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        boolean isAdmin = user != null && user.isAdmin();
        DeviceAggregate aggregate = deviceFacade.unassignForUserAggregate(
                deviceId,
                user != null ? user.id() : null,
                isAdmin
        );
        return toDeviceResponse(aggregate);
    }

    @PostMapping("/api/admin/devices/{device_id}/assign")
    public DeviceDtos.AdminDeviceResponse adminAssignDevice(
            @PathVariable("device_id") Integer deviceId,
            @Valid @RequestBody DeviceDtos.AdminAssignRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        requireAdmin(user);
        UserFacade.UserProfile owner = userFacade.getUser(request.userId());
        if (owner == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "polzovatel' ne najden");
        }
        DeviceSummary summary = deviceFacade.adminAssign(deviceId, request.userId());
        DeviceDtos.DeviceResponse base = mapDeviceResponse(summary);
        DeviceDtos.DeviceOwnerInfoResponse ownerPayload = new DeviceDtos.DeviceOwnerInfoResponse(
                owner.id(),
                owner.email(),
                owner.username()
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
    public DeviceDtos.AdminDeviceResponse adminUnassignDevice(
            @PathVariable("device_id") Integer deviceId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        requireAdmin(user);
        DeviceSummary summary = deviceFacade.adminUnassign(deviceId);
        DeviceDtos.DeviceResponse base = mapDeviceResponse(summary);
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
    public CommonDtos.MessageResponse deleteDevice(
            @PathVariable("device_id") String deviceId
    ) {
        deviceFacade.deleteDevice(deviceId);
        return new CommonDtos.MessageResponse("Device deleted");
    }

    private List<DeviceDtos.DeviceResponse> mapDeviceResponses(List<DeviceSummary> summaries) {
        List<DeviceDtos.DeviceResponse> responses = new ArrayList<>();
        for (DeviceSummary summary : summaries) {
            responses.add(mapDeviceResponse(summary));
        }
        return responses;
    }

    private DeviceDtos.DeviceResponse mapDeviceResponse(DeviceSummary summary) {
        DeviceShadowState state = deviceFacade.getShadowState(summary.deviceId());
        List<SensorView> sensors = sensorFacade.listByDeviceId(summary.id());
        List<PumpView> pumps = pumpFacade.listByDeviceId(summary.id(), state);
        return buildDeviceResponse(summary, sensors, pumps);
    }

    private List<DeviceDtos.SensorResponse> mapSensors(List<SensorView> views) {
        List<DeviceDtos.SensorResponse> result = new ArrayList<>();
        for (SensorView view : views) {
            List<DeviceDtos.BoundPlantResponse> plants = new ArrayList<>();
            if (view.boundPlants() != null) {
                for (var plant : view.boundPlants()) {
                    plants.add(new DeviceDtos.BoundPlantResponse(
                            plant.id(),
                            plant.name(),
                            plant.plantedAt(),
                            plant.growthStage(),
                            plant.ageDays()
                    ));
                }
            }
            result.add(new DeviceDtos.SensorResponse(
                    view.id(),
                    view.type() != null ? view.type().name() : null,
                    view.channel(),
                    view.label(),
                    view.detected(),
                    view.lastValue(),
                    view.lastTs(),
                    plants
            ));
        }
        return result;
    }

    private List<DeviceDtos.PumpResponse> mapPumps(List<PumpView> views) {
        List<DeviceDtos.PumpResponse> result = new ArrayList<>();
        for (PumpView view : views) {
            List<DeviceDtos.PumpBoundPlantResponse> plants = new ArrayList<>();
            if (view.boundPlants() != null) {
                for (var plant : view.boundPlants()) {
                    plants.add(new DeviceDtos.PumpBoundPlantResponse(
                            plant.id(),
                            plant.name(),
                            plant.plantedAt(),
                            plant.ageDays(),
                            plant.rateMlPerHour()
                    ));
                }
            }
            result.add(new DeviceDtos.PumpResponse(
                    view.id(),
                    view.channel(),
                    view.label(),
                    view.isRunning(),
                    plants
            ));
        }
        return result;
    }

    private DeviceDtos.DeviceResponse toDeviceResponse(DeviceAggregate aggregate) {
        return buildDeviceResponse(aggregate.summary(), aggregate.sensors(), aggregate.pumps());
    }

    private DeviceDtos.DeviceResponse buildDeviceResponse(
            DeviceSummary summary,
            List<SensorView> sensors,
            List<PumpView> pumps
    ) {
        List<DeviceDtos.SensorResponse> sensorResponses = mapSensors(sensors);
        List<DeviceDtos.PumpResponse> pumpResponses = mapPumps(pumps);
        return new DeviceDtos.DeviceResponse(
                summary.id(),
                summary.deviceId(),
                summary.name(),
                summary.isOnline(),
                summary.lastSeen(),
                summary.targetMoisture(),
                summary.wateringDuration(),
                summary.wateringTimeout(),
                summary.lightOnHour(),
                summary.lightOffHour(),
                summary.lightDuration(),
                summary.currentVersion(),
                summary.updateAvailable(),
                summary.firmwareVersion(),
                summary.userId(),
                sensorResponses,
                pumpResponses
        );
    }

    private void requireAdmin(AuthenticatedUser user) {
        if (user == null || !user.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Nedostatochno prav");
        }
    }
}
