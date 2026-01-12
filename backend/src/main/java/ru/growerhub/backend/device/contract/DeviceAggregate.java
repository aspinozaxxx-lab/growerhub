package ru.growerhub.backend.device.contract;

import java.util.List;
import ru.growerhub.backend.pump.contract.PumpView;
import ru.growerhub.backend.sensor.contract.SensorView;

public record DeviceAggregate(
        DeviceSummary summary,
        DeviceShadowState state,
        List<SensorView> sensors,
        List<PumpView> pumps
) {
}
