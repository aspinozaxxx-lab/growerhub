package ru.growerhub.backend.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.automation.contract.AutomationData.*;
import ru.growerhub.backend.automation.contract.ControlData.*;
import ru.growerhub.backend.automation.engine.ControlWorker;
import ru.growerhub.backend.automation.engine.RuleWorker;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.mqtt.MqttPublisher;
import ru.growerhub.backend.plant.PlantFacade;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;
import ru.growerhub.backend.zigbee.ZigbeeFacade;
import ru.growerhub.backend.zigbee.contract.ZigbeeControlObservation;

@SpringBootTest(properties = {
        "MQTT_HOST=",
        "spring.datasource.url=jdbc:h2:mem:control_priority;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ControlPriorityIntegrationTest extends IntegrationTestBase {
    private static final Instant START = Instant.parse("2026-10-22T09:00:00Z");
    private static final AtomicInteger RESOURCE_IDS = new AtomicInteger(1000);
    @SpyBean AutomationFacade automation;
    @Autowired UserRepository users;
    @Autowired TestClock clock;
    @MockBean ZigbeeFacade zigbee;
    @MockBean PlantFacade plants;
    @MockBean MqttPublisher mqtt;
    @MockBean RuleWorker ruleWorker;
    @MockBean ControlWorker controlWorker;
    AuthenticatedUser owner;
    AuthenticatedUser otherAdmin;
    UUID coordinator;
    int resourceId;
    Object observed;
    LocalDateTime observedAt;
    boolean online;

    static class TestClock extends Clock {
        Instant value = START;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return value; }
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class TimeConfig {
        @Bean @Primary TestClock controlPriorityClock() { return new TestClock(); }
    }

    @BeforeEach void prepare() {
        clock.value = START;
        coordinator = UUID.randomUUID();
        resourceId = RESOURCE_IDS.incrementAndGet();
        owner = account("owner", "user");
        otherAdmin = account("other", "admin");
        observed = "OFF";
        observedAt = now();
        online = true;
        when(plants.listPlants(any())).thenReturn(List.of());
        doAnswer(call -> overview()).when(automation).getFarmsOverview(owner);
        doReturn(new FarmsOverview(List.of(), null, List.of(), null))
                .when(automation).getFarmsOverview(otherAdmin);
        when(zigbee.controlObservation(eq(owner), any(), anyString(), anyString()))
                .thenAnswer(call -> new ZigbeeControlObservation(observed, observedAt, online));
    }

    @Test void publishedCommandNeedsNewPropertyObservationAndTtlStartsAtAcknowledgement() {
        UUID request = UUID.randomUUID();
        var pending = automation.setManualControl(resourceId, new Manual(request, "ON", 120), owner);
        assertThat(pending.actualConfirmed()).isFalse();
        assertThat(pending.confirmedAt()).isNull();
        dispatchOne();

        clock.value = START.plusSeconds(20);
        automation.reconcileControls(owner.id());
        var awaiting = automation.getControl(resourceId, owner);
        assertThat(awaiting.actualConfirmed()).isFalse();
        assertThat(awaiting.confirmedAt()).isNull();
        assertThat(awaiting.expiresAt()).isNull();

        acknowledge("ON", START.plusSeconds(22));
        var active = automation.getControl(resourceId, owner);
        assertThat(active.actualConfirmed()).isTrue();
        assertThat(active.confirmedAt()).isEqualTo(now());
        assertThat(active.expiresAt()).isEqualTo(now().plusSeconds(120));
        assertThat(active.remainingSeconds()).isEqualTo(120);

        clock.value = START.plusSeconds(52);
        var replay = automation.setManualControl(resourceId, new Manual(request, "ON", 120), owner);
        assertThat(replay.demands()).singleElement().satisfies(d -> {
            assertThat(d.id()).isEqualTo(active.demands().getFirst().id());
            assertThat(d.revision()).isEqualTo(active.demands().getFirst().revision());
            assertThat(d.expiresAt()).isEqualTo(active.expiresAt());
        });
        assertThat(replay.remainingSeconds()).isEqualTo(90);
        assertThatThrownBy(() -> automation.setManualControl(resourceId, new Manual(request, "OFF", 120), owner))
                .isInstanceOf(DomainException.class);
        verify(zigbee, times(1)).setDeviceProperty(owner, coordinator, "0xcontrol", "state", "ON");
        verifyNoInteractions(mqtt);
    }

    @Test void oldStateAndMissingAcknowledgementNeverCauseBlindResendAfterDeadline() {
        automation.setManualControl(resourceId, new Manual(UUID.randomUUID(), "ON", 120), owner);
        dispatchOne();
        observed = "ON";
        observedAt = now().minusSeconds(1);
        automation.reconcileControls(owner.id());
        assertThat(automation.getControl(resourceId, owner).actualConfirmed()).isFalse();

        clock.value = START.plusSeconds(40);
        automation.reconcileControls(owner.id());
        assertThat(automation.getControl(resourceId, owner).status()).isEqualTo("error");
        assertThat(automation.claimControlCommand(owner.id())).isNull();
        clock.value = START.plusSeconds(300);
        automation.reconcileControls(owner.id());
        assertThat(automation.claimControlCommand(owner.id())).isNull();
        verify(zigbee, times(1)).setDeviceProperty(any(), any(), any(), any(), any());
        verifyNoInteractions(mqtt);
    }

    @Test void newerManualCommandCannotBeReleasedByAnOldCardAndReleaseDoesNotForceOff() {
        var first = automation.setManualControl(resourceId, new Manual(UUID.randomUUID(), "ON", 120), owner);
        var old = first.demands().getFirst();
        dispatchOne();
        acknowledge("ON", START.plusSeconds(1));
        var replacement = automation.setManualControl(resourceId, new Manual(UUID.randomUUID(), "OFF", 120), owner);
        automation.releaseManualControl(resourceId, old.id(), old.revision(), owner);
        assertThat(automation.getControl(resourceId, owner).desiredValue()).isEqualTo("OFF");
        var replacementDemand = replacement.demands().getFirst();
        assertThatThrownBy(() -> automation.releaseManualControl(resourceId, replacementDemand.id(), replacementDemand.revision() + 1, owner))
                .isInstanceOf(DomainException.class);
        dispatchOne();
        acknowledge("OFF", START.plusSeconds(2));

        var current = automation.getControl(resourceId, owner).demands().getFirst();
        assertThat(current.id().equals(old.id()) && current.revision() == old.revision()).isFalse();
        automation.releaseManualControl(resourceId, current.id(), current.revision(), owner);
        automation.reconcileControls(owner.id());
        var released = automation.getControl(resourceId, owner);
        assertThat(released.demands()).isEmpty();
        assertThat(released.status()).isEqualTo("idle");
        assertThat(automation.claimControlCommand(owner.id())).isNull();
        verify(zigbee, times(2)).setDeviceProperty(any(), any(), any(), any(), any());
        verifyNoInteractions(mqtt);
    }

    @Test void ambiguousPublishWaitsForActualFactWithoutSendingAgain() {
        automation.setManualControl(resourceId, new Manual(UUID.randomUUID(), "ON", 60), owner);
        var command = automation.claimControlCommand(owner.id());
        assertThat(command).isNotNull();
        doThrow(new IllegalStateException("publish_result_unknown")).when(zigbee)
                .setDeviceProperty(owner, coordinator, "0xcontrol", "state", "ON");
        assertThatThrownBy(() -> automation.executeControlCommand(command)).isInstanceOf(IllegalStateException.class);
        automation.finishControlCommand(command, false);
        automation.reconcileControls(owner.id());
        assertThat(automation.claimControlCommand(owner.id())).isNull();
        acknowledge("ON", START.plusSeconds(5));
        var confirmed = automation.getControl(resourceId, owner);
        assertThat(confirmed.actualConfirmed()).isTrue();
        assertThat(confirmed.status()).isEqualTo("active");
        assertThat(confirmed.expiresAt()).isEqualTo(now().plusSeconds(60));
        assertThat(automation.claimControlCommand(owner.id())).isNull();
        verify(zigbee, times(1)).setDeviceProperty(any(), any(), any(), any(), any());
        verifyNoInteractions(mqtt);
    }

    @Test void invalidOrUnboundedManualCommandNeverReachesTransport() {
        for (var command : List.of(new Manual(UUID.randomUUID(), "TOGGLE", 60),
                new Manual(UUID.randomUUID(), "ON", 0), new Manual(UUID.randomUUID(), "ON", Integer.MAX_VALUE),
                new Manual(null, "ON", 60))) {
            assertThatThrownBy(() -> automation.setManualControl(resourceId, command, owner)).isInstanceOf(DomainException.class);
        }
        assertThat(automation.getControl(resourceId, owner).demands()).isEmpty();
        assertThat(automation.claimControlCommand(owner.id())).isNull();
        verify(zigbee, never()).setDeviceProperty(any(), any(), any(), any(), any());
        verifyNoInteractions(mqtt);
    }

    @Test void persistedExpiryDoesNotMoveOnReconciliationAndExpiresWithoutForcedCommand() {
        automation.setManualControl(resourceId, new Manual(UUID.randomUUID(), "ON", 60), owner);
        dispatchOne();
        acknowledge("ON", START.plusSeconds(3));
        var deadline = automation.getControl(resourceId, owner).expiresAt();
        clock.value = START.plusSeconds(45);
        automation.reconcileControls(owner.id());
        assertThat(automation.getControl(resourceId, owner).expiresAt()).isEqualTo(deadline);
        assertThat(automation.getControl(resourceId, owner).remainingSeconds()).isEqualTo(18);
        clock.value = START.plusSeconds(64);
        automation.reconcileControls(owner.id());
        assertThat(automation.getControl(resourceId, owner).demands()).isEmpty();
        assertThat(automation.claimControlCommand(owner.id())).isNull();
        verify(zigbee, times(1)).setDeviceProperty(any(), any(), any(), any(), any());
    }

    @Test void adminCannotReadWriteOrReleaseSomeoneElsesResource() {
        var status = automation.setManualControl(resourceId, new Manual(UUID.randomUUID(), "ON", 60), owner);
        var demand = status.demands().getFirst();
        assertThatThrownBy(() -> automation.getControl(resourceId, otherAdmin)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> automation.setManualControl(resourceId, new Manual(UUID.randomUUID(), "OFF", 60), otherAdmin))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> automation.releaseManualControl(resourceId, demand.id(), demand.revision(), otherAdmin))
                .isInstanceOf(DomainException.class);
        assertThat(automation.listControls(otherAdmin)).isEmpty();
        verify(zigbee, never()).setDeviceProperty(any(), any(), any(), any(), any());
        verifyNoInteractions(mqtt);
    }

    private void dispatchOne() {
        automation.reconcileControls(owner.id());
        var command = automation.claimControlCommand(owner.id());
        assertThat(command).isNotNull();
        automation.executeControlCommand(command);
        automation.finishControlCommand(command, true);
    }

    private void acknowledge(Object value, Instant at) {
        clock.value = at;
        observed = value;
        observedAt = now();
        automation.reconcileControls(owner.id());
    }

    private AuthenticatedUser account(String name, String role) {
        var key = name + UUID.randomUUID();
        var row = users.save(UserEntity.create(key + "@controls.test", key, role, true, now(), now()));
        return new AuthenticatedUser(row.getId(), role);
    }

    private FarmsOverview overview() {
        var light = new ResourceBinding(resourceId, "ROOM", 1, "LIGHT_SWITCH", "ZIGBEE", null, null,
                coordinator, "0xcontrol", "state", "state", "ON", "OFF", observed, observedAt,
                online ? "ONLINE" : "OFFLINE", null, "Свет", online, null);
        return new FarmsOverview(List.of(new UserFarm(1, "Ферма", true, null, List.of(light), List.of(),
                List.of(), List.of(), List.of(), now(), now())), null, List.of(), null);
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
}
