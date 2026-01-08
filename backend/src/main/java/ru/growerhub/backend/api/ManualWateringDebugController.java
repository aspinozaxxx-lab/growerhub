package ru.growerhub.backend.api;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.CommonDtos;
import ru.growerhub.backend.api.dto.ManualWateringDtos;
import ru.growerhub.backend.device.DeviceService;
import ru.growerhub.backend.mqtt.DebugSettings;
import ru.growerhub.backend.mqtt.MqttSettings;

@RestController
@ConditionalOnProperty(name = "DEBUG", havingValue = "true", matchIfMissing = true)
public class ManualWateringDebugController {
    private final MqttSettings settings;
    private final DebugSettings debugSettings;
    private final DeviceService deviceService;

    public ManualWateringDebugController(
            MqttSettings settings,
            DebugSettings debugSettings,
            DeviceService deviceService
    ) {
        this.settings = settings;
        this.debugSettings = debugSettings;
        this.deviceService = deviceService;
    }

    @GetMapping("/_debug/manual-watering/config")
    public ManualWateringDtos.DebugManualWateringConfigResponse debugConfig() {
        return new ManualWateringDtos.DebugManualWateringConfigResponse(
                settings.getHost(),
                settings.getPort(),
                settings.getUsername(),
                settings.isTls(),
                debugSettings.isDebug()
        );
    }

    @PostMapping("/_debug/shadow/state")
    public CommonDtos.OkResponse debugShadowState(
            @Valid @RequestBody ManualWateringDtos.ShadowStateRequest request
    ) {
        deviceService.handleState(request.deviceId(), request.state(), java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        return new CommonDtos.OkResponse(true);
    }

    @GetMapping("/_debug/manual-watering/snapshot")
    public ManualWateringDtos.DebugManualWateringSnapshotResponse debugSnapshot(
            @RequestParam("device_id") String deviceId
    ) {
        Object raw = deviceService.debugShadowDump(deviceId);
        Object view = deviceService.debugManualWateringView(deviceId);
        return new ManualWateringDtos.DebugManualWateringSnapshotResponse(raw, view);
    }
}
