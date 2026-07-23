package ru.growerhub.backend.api;

import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.ZigbeeDtos;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.zigbee.ZigbeeFacade;

@RestController
@RequestMapping("/api/zigbee/coordinators")
public class ZigbeeController {
    private final ZigbeeFacade zigbeeFacade;

    public ZigbeeController(ZigbeeFacade zigbeeFacade) {
        this.zigbeeFacade = zigbeeFacade;
    }

    @PostMapping
    public ResponseEntity<ZigbeeDtos.CoordinatorCreatedResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody ZigbeeDtos.CreateCoordinatorRequest request
    ) {
        ZigbeeDtos.CoordinatorCreatedResponse response = ZigbeeApiMapper.toCoordinatorCreated(
                zigbeeFacade.createCoordinator(user, request != null ? request.name() : null)
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(response);
    }

    @GetMapping
    public List<ZigbeeDtos.CoordinatorSummaryResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return zigbeeFacade.listCoordinators(user).stream()
                .map(ZigbeeApiMapper::toCoordinatorSummary)
                .toList();
    }

    @GetMapping("/{coordinator_id}")
    public ZigbeeDtos.CoordinatorSummaryResponse get(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("coordinator_id") UUID coordinatorId
    ) {
        return ZigbeeApiMapper.toCoordinatorSummary(zigbeeFacade.getCoordinator(user, coordinatorId));
    }

    @DeleteMapping("/{coordinator_id}")
    public ResponseEntity<Void> archive(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("coordinator_id") UUID coordinatorId
    ) {
        zigbeeFacade.archiveCoordinator(user, coordinatorId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{coordinator_id}/credentials/rotate")
    public ResponseEntity<ZigbeeDtos.CoordinatorSetupResponse> rotate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("coordinator_id") UUID coordinatorId
    ) {
        ZigbeeDtos.CoordinatorSetupResponse response = ZigbeeApiMapper.toCoordinatorSetup(
                zigbeeFacade.rotateCoordinatorCredentials(user, coordinatorId)
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(response);
    }

    @GetMapping("/{coordinator_id}/overview")
    public ZigbeeDtos.OverviewResponse overview(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("coordinator_id") UUID coordinatorId
    ) {
        return ZigbeeApiMapper.toOverview(zigbeeFacade.getOverview(user, coordinatorId));
    }

    @GetMapping("/{coordinator_id}/devices/{ieee_address}/history")
    public List<ZigbeeDtos.HistoryPointResponse> history(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("coordinator_id") UUID coordinatorId,
            @PathVariable("ieee_address") String ieeeAddress,
            @RequestParam("property") String property,
            @RequestParam(value = "hours", required = false) Integer hours
    ) {
        return zigbeeFacade.getHistory(user, coordinatorId, ieeeAddress, property, hours).stream()
                .map(ZigbeeApiMapper::toHistoryPoint)
                .toList();
    }

    @PostMapping("/{coordinator_id}/permit-join")
    public ZigbeeDtos.CommandPublishResponse permitJoin(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("coordinator_id") UUID coordinatorId,
            @RequestBody(required = false) ZigbeeDtos.PermitJoinRequest request
    ) {
        return ZigbeeApiMapper.toCommandPublish(zigbeeFacade.permitJoin(
                user,
                coordinatorId,
                request != null ? request.seconds() : null
        ));
    }

    @PostMapping("/{coordinator_id}/devices/{ieee_address}/set-state")
    public ZigbeeDtos.CommandPublishResponse setState(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("coordinator_id") UUID coordinatorId,
            @PathVariable("ieee_address") String ieeeAddress,
            @RequestBody ZigbeeDtos.SetStateRequest request
    ) {
        return ZigbeeApiMapper.toCommandPublish(zigbeeFacade.setDeviceState(
                user,
                coordinatorId,
                ieeeAddress,
                request != null ? request.state() : null
        ));
    }

    @PostMapping("/{coordinator_id}/devices/{ieee_address}/set")
    public ZigbeeDtos.CommandPublishResponse setProperty(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("coordinator_id") UUID coordinatorId,
            @PathVariable("ieee_address") String ieeeAddress,
            @RequestBody ZigbeeDtos.SetPropertyRequest request
    ) {
        return ZigbeeApiMapper.toCommandPublish(zigbeeFacade.setDeviceProperty(
                user,
                coordinatorId,
                ieeeAddress,
                request != null ? request.property() : null,
                request != null ? request.value() : null
        ));
    }

    @PostMapping("/{coordinator_id}/devices/{ieee_address}/rename")
    public ZigbeeDtos.CommandPublishResponse rename(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("coordinator_id") UUID coordinatorId,
            @PathVariable("ieee_address") String ieeeAddress,
            @RequestBody ZigbeeDtos.RenameRequest request
    ) {
        return ZigbeeApiMapper.toCommandPublish(zigbeeFacade.renameDevice(
                user,
                coordinatorId,
                ieeeAddress,
                request != null ? request.friendlyName() : null
        ));
    }
}
