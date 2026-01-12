package ru.growerhub.backend.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.growerhub.backend.device.DeviceSettings;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.device.internal.DeviceRepository;
import ru.growerhub.backend.device.internal.DeviceShadowStore;
import ru.growerhub.backend.device.internal.DeviceStateLastRepository;

class DeviceShadowStoreTest {

    @Test
    void reportsOnlineAndOfflineByThreshold() {
        MutableClock clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"));
        DeviceSettings settings = new DeviceSettings();
        settings.setOnlineThresholdS(30);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        DeviceRepository deviceRepository = Mockito.mock(DeviceRepository.class);
        DeviceStateLastRepository stateRepository = Mockito.mock(DeviceStateLastRepository.class);
        DeviceShadowStore store = new DeviceShadowStore(
                settings,
                mapper,
                clock,
                deviceRepository,
                stateRepository
        );

        DeviceShadowState.ManualWateringState manual = new DeviceShadowState.ManualWateringState(
                "running",
                20,
                null,
                null,
                "corr"
        );
        store.updateFromState("device-1", new DeviceShadowState(manual, null, null, null, null, null, null, null, null, null));

        Assertions.assertEquals(true, store.getManualWateringView("device-1").get("is_online"));

        clock.advance(Duration.ofSeconds(31));
        Assertions.assertEquals(false, store.getManualWateringView("device-1").get("is_online"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void advance(Duration delta) {
            instant = instant.plus(delta);
        }
    }
}

