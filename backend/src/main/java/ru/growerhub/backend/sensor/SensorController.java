package ru.growerhub.backend.sensor;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.CommonDtos;
import ru.growerhub.backend.api.dto.HistoryDtos;
import ru.growerhub.backend.api.dto.SensorDtos;
import ru.growerhub.backend.common.contract.AuthenticatedUser;

@RestController
public class SensorController {
    private final SensorFacade sensorFacade;

    public SensorController(SensorFacade sensorFacade) {
        this.sensorFacade = sensorFacade;
    }

    @PutMapping("/api/sensors/{sensor_id}/bindings")
    public CommonDtos.OkResponse updateBindings(
            @PathVariable("sensor_id") Integer sensorId,
            @RequestBody SensorDtos.SensorBindingUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        sensorFacade.updateBindings(sensorId, request.plantIds(), user);
        return new CommonDtos.OkResponse(true);
    }

    @GetMapping("/api/sensors/{sensor_id}/history")
    public List<HistoryDtos.SensorHistoryPointResponse> getSensorHistory(
            @PathVariable("sensor_id") Integer sensorId,
            @RequestParam(value = "hours", defaultValue = "24") Integer hours,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return sensorFacade.getHistory(sensorId, hours, user).stream()
                .map(point -> new HistoryDtos.SensorHistoryPointResponse(point.ts(), point.value()))
                .toList();
    }
}

