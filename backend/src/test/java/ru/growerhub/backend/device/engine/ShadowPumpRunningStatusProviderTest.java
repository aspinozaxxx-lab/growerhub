package ru.growerhub.backend.device.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import ru.growerhub.backend.device.contract.DeviceShadowState;

class ShadowPumpRunningStatusProviderTest {
    private static final String DEVICE_ID = "pump-device";

    @Test
    void nonRunningManualStatusDoesNotHideEnabledRelay() {
        DeviceShadowStore shadowStore = mock(DeviceShadowStore.class);
        ShadowPumpRunningStatusProvider provider = new ShadowPumpRunningStatusProvider(shadowStore);

        for (String status : new String[]{"idle", "completed", "pause"}) {
            when(shadowStore.getSnapshotOrLoad(DEVICE_ID)).thenReturn(snapshot(status, "on"));
            assertTrue(provider.isPumpRunning(DEVICE_ID, 0), status);
        }

        when(shadowStore.getSnapshotOrLoad(DEVICE_ID)).thenReturn(snapshot("idle", "off"));
        assertFalse(provider.isPumpRunning(DEVICE_ID, 0));
    }

    @Test
    void activeManualStatusRemainsConservativeWhenRelayIsOff() {
        DeviceShadowStore shadowStore = mock(DeviceShadowStore.class);
        ShadowPumpRunningStatusProvider provider = new ShadowPumpRunningStatusProvider(shadowStore);

        for (String status : new String[]{"running", "stopping"}) {
            when(shadowStore.getSnapshotOrLoad(DEVICE_ID)).thenReturn(snapshot(status, "off"));
            assertTrue(provider.isPumpRunning(DEVICE_ID, 0), status);
        }
    }

    private DeviceShadowStore.DeviceSnapshot snapshot(String manualStatus, String relayStatus) {
        DeviceShadowState state = new DeviceShadowState(
                new DeviceShadowState.ManualWateringState(
                        manualStatus,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new DeviceShadowState.RelayState(relayStatus),
                null
        );
        return new DeviceShadowStore.DeviceSnapshot(
                state,
                LocalDateTime.now(),
                true,
                DeviceShadowStore.SnapshotSource.MEMORY
        );
    }
}
