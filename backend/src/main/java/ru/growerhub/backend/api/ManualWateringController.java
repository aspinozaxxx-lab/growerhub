package ru.growerhub.backend.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.CommonDtos;
import ru.growerhub.backend.api.dto.ManualWateringDtos;

@RestController
public class ManualWateringController {

    @PostMapping("/api/manual-watering/start")
    public ManualWateringDtos.ManualWateringStartResponse start(
            @RequestBody ManualWateringDtos.ManualWateringStartRequest request
    ) {
        throw todo();
    }

    @PostMapping("/api/manual-watering/stop")
    public ManualWateringDtos.ManualWateringStopResponse stop(
            @RequestBody ManualWateringDtos.ManualWateringStopRequest request
    ) {
        throw todo();
    }

    @PostMapping("/api/manual-watering/reboot")
    public ManualWateringDtos.ManualWateringRebootResponse reboot(
            @RequestBody ManualWateringDtos.ManualWateringRebootRequest request
    ) {
        throw todo();
    }

    @GetMapping("/api/manual-watering/status")
    public ManualWateringDtos.ManualWateringStatusResponse status(
            @RequestParam("device_id") String deviceId
    ) {
        throw todo();
    }

    @GetMapping("/api/manual-watering/ack")
    public ManualWateringDtos.ManualWateringAckResponse ack(
            @RequestParam("correlation_id") String correlationId
    ) {
        throw todo();
    }

    @GetMapping("/api/manual-watering/wait-ack")
    public ManualWateringDtos.ManualWateringAckResponse waitAck(
            @RequestParam("correlation_id") String correlationId,
            @RequestParam(value = "timeout_s", defaultValue = "5") Integer timeoutSeconds
    ) {
        throw todo();
    }

    @GetMapping("/_debug/manual-watering/config")
    public ManualWateringDtos.DebugManualWateringConfigResponse debugConfig() {
        throw todo();
    }

    @PostMapping("/_debug/shadow/state")
    public CommonDtos.OkResponse debugShadowState(
            @RequestBody ManualWateringDtos.ShadowStateRequest request
    ) {
        throw todo();
    }

    @GetMapping("/_debug/manual-watering/snapshot")
    public ManualWateringDtos.DebugManualWateringSnapshotResponse debugSnapshot(
            @RequestParam("device_id") String deviceId
    ) {
        throw todo();
    }

    private static ApiException todo() {
        return new ApiException(HttpStatus.NOT_IMPLEMENTED, "TODO");
    }
}
