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
import ru.growerhub.backend.device.DeviceShadowStore;
import ru.growerhub.backend.mqtt.DebugSettings;
import ru.growerhub.backend.mqtt.MqttSettings;

@RestController
@ConditionalOnProperty(name = "DEBUG", havingValue = "true", matchIfMissing = true)
public class ManualWateringDebugController {
    private final MqttSettings settings;
    private final DebugSettings debugSettings;
    private final DeviceShadowStore shadowStore;

    public ManualWateringDebugController(MqttSettings settings, DebugSettings debugSettings, DeviceShadowStore shadowStore) {
        this.settings = settings;
        this.debugSettings = debugSettings;
        this.shadowStore = shadowStore;
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
        shadowStore.updateFromState(request.deviceId(), request.state());
        return new CommonDtos.OkResponse(true);
    }

    @GetMapping("/_debug/manual-watering/snapshot")
    public ManualWateringDtos.DebugManualWateringSnapshotResponse debugSnapshot(
            @RequestParam("device_id") String deviceId
    ) {
        Object raw = shadowStore.debugDump(deviceId);
        Object view = shadowStore.getManualWateringView(deviceId);
        return new ManualWateringDtos.DebugManualWateringSnapshotResponse(raw, view);
    }
}
