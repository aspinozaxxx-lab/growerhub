package ru.growerhub.backend.api;

import ru.growerhub.backend.api.dto.ZigbeeDtos;
import ru.growerhub.backend.zigbee.contract.ZigbeeBridgeData;
import ru.growerhub.backend.zigbee.contract.ZigbeeCommandPublishResult;
import ru.growerhub.backend.zigbee.contract.ZigbeeCommandResponseData;
import ru.growerhub.backend.zigbee.contract.ZigbeeCoordinatorCreated;
import ru.growerhub.backend.zigbee.contract.ZigbeeCoordinatorData;
import ru.growerhub.backend.zigbee.contract.ZigbeeCoordinatorSetup;
import ru.growerhub.backend.zigbee.contract.ZigbeeCoordinatorSummary;
import ru.growerhub.backend.zigbee.contract.ZigbeeDeviceData;
import ru.growerhub.backend.zigbee.contract.ZigbeeFeatureData;
import ru.growerhub.backend.zigbee.contract.ZigbeeHistoryPoint;
import ru.growerhub.backend.zigbee.contract.ZigbeeOverviewData;

final class ZigbeeApiMapper {
    private ZigbeeApiMapper() {
    }

    static ZigbeeDtos.CoordinatorCreatedResponse toCoordinatorCreated(ZigbeeCoordinatorCreated data) {
        return new ZigbeeDtos.CoordinatorCreatedResponse(
                toCoordinatorSummary(data.coordinator()),
                toCoordinatorSetup(data.setup())
        );
    }

    static ZigbeeDtos.CoordinatorSummaryResponse toCoordinatorSummary(ZigbeeCoordinatorSummary data) {
        return new ZigbeeDtos.CoordinatorSummaryResponse(
                data.id(),
                data.name(),
                data.mqttUsername(),
                data.baseTopic(),
                data.status().name(),
                data.deviceCount(),
                data.lastSeenAt(),
                data.connectedAt(),
                data.firstDeviceSeenAt(),
                data.createdAt(),
                data.updatedAt()
        );
    }

    static ZigbeeDtos.CoordinatorSetupResponse toCoordinatorSetup(ZigbeeCoordinatorSetup data) {
        return new ZigbeeDtos.CoordinatorSetupResponse(
                data.server(),
                data.username(),
                data.password(),
                data.clientId(),
                data.baseTopic(),
                data.configurationYaml(),
                data.secretYaml()
        );
    }

    static ZigbeeDtos.OverviewResponse toOverview(ZigbeeOverviewData data) {
        return new ZigbeeDtos.OverviewResponse(
                toBridge(data.bridge()),
                toPhysicalCoordinator(data.coordinator()),
                data.devices().stream().map(ZigbeeApiMapper::toDevice).toList(),
                toCommandResponse(data.lastCommandResponse())
        );
    }

    static ZigbeeDtos.HistoryPointResponse toHistoryPoint(ZigbeeHistoryPoint point) {
        return new ZigbeeDtos.HistoryPointResponse(
                point.ts(),
                point.property(),
                point.value(),
                point.rawValue(),
                point.valueText(),
                point.valueBoolean()
        );
    }

    static ZigbeeDtos.CommandPublishResponse toCommandPublish(ZigbeeCommandPublishResult result) {
        return new ZigbeeDtos.CommandPublishResponse(result.message(), result.topic());
    }

    private static ZigbeeDtos.BridgeResponse toBridge(ZigbeeBridgeData data) {
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

    private static ZigbeeDtos.CoordinatorResponse toPhysicalCoordinator(ZigbeeCoordinatorData data) {
        return data != null
                ? new ZigbeeDtos.CoordinatorResponse(data.ieeeAddress(), data.friendlyName(), data.data())
                : null;
    }

    private static ZigbeeDtos.DeviceResponse toDevice(ZigbeeDeviceData data) {
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
                data.features().stream().map(ZigbeeApiMapper::toFeature).toList(),
                data.metrics().stream().map(ZigbeeApiMapper::toFeature).toList(),
                data.controls().stream().map(ZigbeeApiMapper::toFeature).toList(),
                data.state(),
                data.availability(),
                data.lastStateAt(),
                data.updatedAt()
        );
    }

    private static ZigbeeDtos.FeatureResponse toFeature(ZigbeeFeatureData data) {
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

    private static ZigbeeDtos.CommandResponse toCommandResponse(ZigbeeCommandResponseData data) {
        return data != null
                ? new ZigbeeDtos.CommandResponse(
                        data.topic(),
                        data.status(),
                        data.error(),
                        data.response(),
                        data.updatedAt()
                )
                : null;
    }
}
