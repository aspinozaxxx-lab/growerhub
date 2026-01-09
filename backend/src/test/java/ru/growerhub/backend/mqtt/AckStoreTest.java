package ru.growerhub.backend.mqtt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.growerhub.backend.device.AckSettings;
import ru.growerhub.backend.mqtt.model.ManualWateringAck;

class AckStoreTest {

    @Test
    void expiresAckByTtl() {
        MutableClock clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"));
        AckSettings settings = new AckSettings();
        settings.setTtlSeconds(10);
        AckStore store = new AckStore(settings, clock);

        ManualWateringAck ack = new ManualWateringAck("corr-1", "accepted", null, "ok");
        store.put("device-1", ack);

        Assertions.assertNotNull(store.get("corr-1"));

        clock.advance(Duration.ofSeconds(11));
        Assertions.assertNull(store.get("corr-1"));
        Assertions.assertEquals(0, store.cleanupExpired());
    }

    @Test
    void keepsAckWhenTtlDisabled() {
        MutableClock clock = new MutableClock(Instant.parse("2025-01-01T00:00:00Z"));
        AckSettings settings = new AckSettings();
        settings.setTtlSeconds(0);
        AckStore store = new AckStore(settings, clock);

        ManualWateringAck ack = new ManualWateringAck("corr-2", "accepted", null, "ok");
        store.put("device-1", ack);

        clock.advance(Duration.ofMinutes(5));
        Assertions.assertNotNull(store.get("corr-2"));
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

