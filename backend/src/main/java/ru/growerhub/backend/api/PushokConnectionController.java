package ru.growerhub.backend.api;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.growerhub.backend.api.dto.ZigbeeDtos;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.zigbee.ZigbeeFacade;

@RestController
@RequestMapping("/api/zigbee/pushok")
public class PushokConnectionController {
    private final ZigbeeFacade facade;
    public PushokConnectionController(ZigbeeFacade facade) { this.facade = facade; }
    public record ConnectRequest(String name, @com.fasterxml.jackson.annotation.JsonProperty("hub_id") String hubId,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.AssertTrue
            @com.fasterxml.jackson.annotation.JsonProperty("confirm_access") Boolean confirmAccess) { }
    @GetMapping("/availability")
    public Map<String, Boolean> availability(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("available", facade.isPushokAvailable(user));
    }
    @PostMapping
    public ResponseEntity<ZigbeeDtos.CoordinatorSummaryResponse> connect(@AuthenticationPrincipal AuthenticatedUser user,
            @jakarta.validation.Valid @RequestBody ConnectRequest request) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(ZigbeeApiMapper.toCoordinatorSummary(
                facade.createPushokCoordinator(user, request.name(), request.hubId())));
    }
    @PostMapping("/{coordinator_id}/pair")
    public ResponseEntity<ZigbeeDtos.CoordinatorSummaryResponse> retry(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("coordinator_id") UUID coordinatorId) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(ZigbeeApiMapper.toCoordinatorSummary(
                facade.retryPushokPairing(user, coordinatorId)));
    }
}
