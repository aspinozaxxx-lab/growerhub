package ru.growerhub.backend.pump.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import ru.growerhub.backend.device.DeviceFacade;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.plant.PlantFacade;
import ru.growerhub.backend.pump.contract.PumpRunningStatusProvider;
import ru.growerhub.backend.pump.jpa.PumpEntity;
import ru.growerhub.backend.pump.jpa.PumpPlantBindingRepository;

class PumpQueryServiceTest {
    @Test
    void inactiveManualStatusDoesNotHideEnabledRelay() {
        PumpQueryService service = service();

        for (String status : new String[]{"idle", "completed", "pause"}) {
            assertTrue(service.listByDevice(7, state(status, "on")).get(0).isRunning(), status);
        }
        assertFalse(service.listByDevice(7, state("idle", "off")).get(0).isRunning());
    }

    private PumpQueryService service() {
        PumpService pumpService = mock(PumpService.class);
        PumpPlantBindingRepository bindingRepository = mock(PumpPlantBindingRepository.class);
        PumpEntity pump = PumpEntity.create();
        pump.setDeviceId(7);
        pump.setChannel(0);
        when(pumpService.listByDevice(7, false)).thenReturn(List.of(pump));
        when(bindingRepository.findAllByPump_IdIn(anyList())).thenReturn(List.of());
        return new PumpQueryService(
                pumpService,
                bindingRepository,
                mock(PumpRunningStatusProvider.class),
                mock(DeviceFacade.class),
                mock(PlantFacade.class)
        );
    }

    private DeviceShadowState state(String manualStatus, String relayStatus) {
        return new DeviceShadowState(
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
    }
}
