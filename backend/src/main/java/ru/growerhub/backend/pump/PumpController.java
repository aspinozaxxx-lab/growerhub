package ru.growerhub.backend.pump;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.ApiException;
import ru.growerhub.backend.api.dto.CommonDtos;
import ru.growerhub.backend.api.dto.PumpDtos;
import ru.growerhub.backend.common.contract.AuthenticatedUser;

@RestController
public class PumpController {
    private final PumpFacade pumpFacade;

    public PumpController(PumpFacade pumpFacade) {
        this.pumpFacade = pumpFacade;
    }

    @PutMapping("/api/pumps/{pump_id}/bindings")
    public CommonDtos.OkResponse updateBindings(
            @PathVariable("pump_id") Integer pumpId,
            @Valid @RequestBody PumpDtos.PumpBindingUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        pumpFacade.updateBindings(
                pumpId,
                request.items() != null
                        ? request.items().stream()
                        .map(item -> new PumpFacade.PumpBindingItem(item.plantId(), item.rateMlPerHour()))
                        .toList()
                        : null,
                user
        );
        return new CommonDtos.OkResponse(true);
    }

    @PostMapping("/api/pumps/{pump_id}/watering/start")
    public PumpDtos.PumpWateringStartResponse start(
            @PathVariable("pump_id") Integer pumpId,
            @Valid @RequestBody PumpDtos.PumpWateringStartRequest request,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        var result = pumpFacade.start(
                pumpId,
                new PumpFacade.PumpWateringRequest(
                        request.durationS(),
                        request.waterVolumeL(),
                        request.ph(),
                        request.fertilizersPerLiter()
                ),
                user
        );
        return new PumpDtos.PumpWateringStartResponse(result.correlationId());
    }

    @PostMapping("/api/pumps/{pump_id}/watering/stop")
    public PumpDtos.PumpWateringStopResponse stop(
            @PathVariable("pump_id") Integer pumpId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        var result = pumpFacade.stop(pumpId, user);
        return new PumpDtos.PumpWateringStopResponse(result.correlationId());
    }

    @PostMapping("/api/pumps/{pump_id}/watering/reboot")
    public PumpDtos.PumpWateringRebootResponse reboot(
            @PathVariable("pump_id") Integer pumpId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        var result = pumpFacade.reboot(pumpId, user);
        return new PumpDtos.PumpWateringRebootResponse(result.correlationId(), result.message());
    }

    @GetMapping("/api/pumps/{pump_id}/watering/status")
    public PumpDtos.PumpWateringStatusResponse status(
            @PathVariable("pump_id") Integer pumpId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        var result = pumpFacade.status(pumpId, user);
        Map<String, Object> view = result.view();
        if (view == null) {
            return new PumpDtos.PumpWateringStatusResponse(
                    "idle",
                    null,
                    0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    "device_offline",
                    "no_state"
            );
        }
        return new PumpDtos.PumpWateringStatusResponse(
                asString(view.get("status")),
                asInteger(view.get("duration_s")),
                asInteger(view.get("duration")),
                asString(view.get("started_at")),
                asString(view.get("start_time")),
                asInteger(view.get("remaining_s")),
                asString(view.get("correlation_id")),
                asString(view.get("updated_at")),
                asString(view.get("last_seen_at")),
                asBoolean(view.get("is_online")),
                asString(view.get("offline_reason")),
                asString(view.get("source"))
        );
    }

    @GetMapping("/api/pumps/watering/ack")
    public PumpDtos.PumpWateringAckResponse ack(
            @RequestParam("correlation_id") String correlationId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        PumpAck ack = pumpFacade.getAck(correlationId);
        if (ack == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ACK eshche ne poluchen ili udalen po TTL");
        }
        return new PumpDtos.PumpWateringAckResponse(
                ack.correlationId(),
                ack.result(),
                ack.reason(),
                ack.status()
        );
    }

    @GetMapping("/api/pumps/watering/wait-ack")
    public PumpDtos.PumpWateringAckResponse waitAck(
            @RequestParam("correlation_id") String correlationId,
            @RequestParam(value = "timeout_s", defaultValue = "5") @Min(1) @Max(15) Integer timeoutSeconds,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        long deadline = System.nanoTime() + timeoutSeconds * 1_000_000_000L;
        while (true) {
            PumpAck ack = pumpFacade.getAck(correlationId);
            if (ack != null) {
                return new PumpDtos.PumpWateringAckResponse(
                        ack.correlationId(),
                        ack.result(),
                        ack.reason(),
                        ack.status()
                );
            }
            if (System.nanoTime() >= deadline) {
                throw new ApiException(
                        HttpStatus.REQUEST_TIMEOUT,
                        "ACK ne poluchen v zadannoe vremya"
                );
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ApiException(
                        HttpStatus.REQUEST_TIMEOUT,
                        "ACK ne poluchen v zadannoe vremya"
                );
            }
        }
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }
}
