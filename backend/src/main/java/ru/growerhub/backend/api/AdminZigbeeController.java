package ru.growerhub.backend.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.growerhub.backend.api.dto.ZigbeeDtos;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.ZigbeeBridgeData;
import ru.growerhub.backend.zigbee.contract.ZigbeeCommandPublishResult;
import ru.growerhub.backend.zigbee.contract.ZigbeeCommandResponseData;
import ru.growerhub.backend.zigbee.contract.ZigbeeCoordinatorData;
import ru.growerhub.backend.zigbee.contract.ZigbeeDeviceData;
import ru.growerhub.backend.zigbee.contract.ZigbeeFeatureData;
import ru.growerhub.backend.zigbee.contract.ZigbeeOverviewData;

@RestController
@Validated
public class AdminZigbeeController {
    private final ZigbeeFacade zigbeeFacade;

    public AdminZigbeeController(ZigbeeFacade zigbeeFacade) {
        this.zigbeeFacade = zigbeeFacade;
    }

    @GetMapping("/api/admin/zigbee")
    public ZigbeeDtos.OverviewResponse getOverview(@AuthenticationPrincipal AuthenticatedUser user) {
        requireAdmin(user);
        return toOverviewResponse(zigbeeFacade.getOverview());
    }

    @PostMapping("/api/admin/zigbee/permit-join")
    public ZigbeeDtos.CommandPublishResponse permitJoin(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody(required = false) ZigbeeDtos.PermitJoinRequest request
    ) {
        requireAdmin(user);
        ZigbeeCommandPublishResult result = zigbeeFacade.permitJoin(request != null ? request.seconds() : null);
        return toCommandPublishResponse(result);
    }

    @PostMapping("/api/admin/zigbee/devices/{ieee_address}/set-state")
    public ZigbeeDtos.CommandPublishResponse setDeviceState(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("ieee_address") String ieeeAddress,
            @RequestBody ZigbeeDtos.SetStateRequest request
    ) {
        requireAdmin(user);
        ZigbeeCommandPublishResult result = zigbeeFacade.setDeviceState(
                ieeeAddress,
                request != null ? request.state() : null
        );
        return toCommandPublishResponse(result);
    }

    @PostMapping("/api/admin/zigbee/devices/{ieee_address}/set")
    public ZigbeeDtos.CommandPublishResponse setDeviceProperty(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("ieee_address") String ieeeAddress,
            @RequestBody ZigbeeDtos.SetPropertyRequest request
    ) {
        requireAdmin(user);
        ZigbeeCommandPublishResult result = zigbeeFacade.setDeviceProperty(
                ieeeAddress,
                request != null ? request.property() : null,
                request != null ? request.value() : null
        );
        return toCommandPublishResponse(result);
    }

    @PostMapping("/api/admin/zigbee/devices/{ieee_address}/rename")
    public ZigbeeDtos.CommandPublishResponse renameDevice(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable("ieee_address") String ieeeAddress,
            @RequestBody ZigbeeDtos.RenameRequest request
    ) {
        requireAdmin(user);
        ZigbeeCommandPublishResult result = zigbeeFacade.renameDevice(
                ieeeAddress,
                request != null ? request.friendlyName() : null
        );
        return toCommandPublishResponse(result);
    }

    private ZigbeeDtos.OverviewResponse toOverviewResponse(ZigbeeOverviewData data) {
        return new ZigbeeDtos.OverviewResponse(
                toBridgeResponse(data.bridge()),
                toCoordinatorResponse(data.coordinator()),
                data.devices().stream().map(this::toDeviceResponse).toList(),
                toCommandResponse(data.lastCommandResponse())
        );
    }

    private ZigbeeDtos.BridgeResponse toBridgeResponse(ZigbeeBridgeData data) {
        if (data == null) {
            return null;
        }
        return new ZigbeeDtos.BridgeResponse(
                data.baseTopic(),
                data.state(),
                data.info(),
                data.permitJoin(),
                data.permitJoinEnd(),
                data.version(),
                data.updatedAt()
        );
    }

    private ZigbeeDtos.CoordinatorResponse toCoordinatorResponse(ZigbeeCoordinatorData data) {
        if (data == null) {
            return null;
        }
        return new ZigbeeDtos.CoordinatorResponse(data.ieeeAddress(), data.friendlyName(), data.data());
    }

    private ZigbeeDtos.DeviceResponse toDeviceResponse(ZigbeeDeviceData data) {
        return new ZigbeeDtos.DeviceResponse(
                data.id(),
                data.ieeeAddress(),
                data.friendlyName(),
                data.type(),
                data.supported(),
                data.disabled(),
                data.coordinator(),
                data.bridgeDevice(),
                data.definition(),
                data.imageUrl(),
                data.features().stream().map(this::toFeatureResponse).toList(),
                data.metrics().stream().map(this::toFeatureResponse).toList(),
                data.controls().stream().map(this::toFeatureResponse).toList(),
                data.state(),
                data.availability(),
                data.lastStateAt(),
                data.updatedAt()
        );
    }

    private ZigbeeDtos.FeatureResponse toFeatureResponse(ZigbeeFeatureData data) {
        return new ZigbeeDtos.FeatureResponse(
                data.type(),
                data.property(),
                data.name(),
                data.label(),
                data.description(),
                data.access(),
                data.unit(),
                data.values(),
                data.valueMin(),
                data.valueMax(),
                data.valueStep(),
                data.valueOn(),
                data.valueOff(),
                data.valueToggle(),
                data.endpoint(),
                data.value()
        );
    }

    private ZigbeeDtos.CommandResponse toCommandResponse(ZigbeeCommandResponseData data) {
        if (data == null) {
            return null;
        }
        return new ZigbeeDtos.CommandResponse(
                data.topic(),
                data.status(),
                data.error(),
                data.response(),
                data.updatedAt()
        );
    }

    private ZigbeeDtos.CommandPublishResponse toCommandPublishResponse(ZigbeeCommandPublishResult result) {
        return new ZigbeeDtos.CommandPublishResponse(result.message(), result.topic());
    }

    private void requireAdmin(AuthenticatedUser user) {
        if (user == null || !user.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Nedostatochno prav");
        }
    }
}
