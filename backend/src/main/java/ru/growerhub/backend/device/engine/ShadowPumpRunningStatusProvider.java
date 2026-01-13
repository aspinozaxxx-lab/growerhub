package ru.growerhub.backend.device.engine;

import org.springframework.stereotype.Service;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.pump.PumpRunningStatusProvider;

@Service
public class ShadowPumpRunningStatusProvider implements PumpRunningStatusProvider {
    private final DeviceShadowStore shadowStore;

    public ShadowPumpRunningStatusProvider(DeviceShadowStore shadowStore) {
        this.shadowStore = shadowStore;
    }

    @Override
    public boolean isPumpRunning(String deviceId, int pumpChannel) {
        if (deviceId == null || deviceId.isBlank()) {
            return false;
        }
        if (pumpChannel != 0) {
            return false;
        }
        DeviceShadowStore.DeviceSnapshot snapshot = shadowStore.getSnapshotOrLoad(deviceId);
        DeviceShadowState state = snapshot != null ? snapshot.state() : null;
        if (state == null) {
            return false;
        }
        if (state.manualWatering() != null && state.manualWatering().status() != null) {
            return "running".equals(state.manualWatering().status());
        }
        DeviceShadowState.RelayState relay = state.pump();
        if (relay != null && relay.status() != null) {
            return "on".equalsIgnoreCase(relay.status());
        }
        return false;
    }
}
