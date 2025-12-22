package ru.growerhub.backend.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.HistoryDtos;

@RestController
public class HistoryController {

    @GetMapping("/api/device/{device_id}/sensor-history")
    public List<HistoryDtos.SensorDataPointResponse> getSensorHistory(
            @PathVariable("device_id") String deviceId,
            @RequestParam(value = "hours", defaultValue = "24") Integer hours
    ) {
        throw todo();
    }

    @GetMapping("/api/device/{device_id}/watering-logs")
    public List<HistoryDtos.WateringLogResponse> getWateringLogs(
            @PathVariable("device_id") String deviceId,
            @RequestParam(value = "days", defaultValue = "7") Integer days
    ) {
        throw todo();
    }

    private static ApiException todo() {
        return new ApiException(HttpStatus.NOT_IMPLEMENTED, "TODO");
    }
}
