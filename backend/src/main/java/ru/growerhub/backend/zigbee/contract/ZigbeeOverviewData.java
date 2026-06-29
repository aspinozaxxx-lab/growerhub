package ru.growerhub.backend.zigbee.contract;

import java.util.List;

public record ZigbeeOverviewData(
        ZigbeeBridgeData bridge,
        ZigbeeCoordinatorData coordinator,
        List<ZigbeeDeviceData> devices,
        ZigbeeCommandResponseData lastCommandResponse
) {
}
