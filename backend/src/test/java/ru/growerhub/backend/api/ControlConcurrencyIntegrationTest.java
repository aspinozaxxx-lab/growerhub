package ru.growerhub.backend.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.automation.AutomationFacade;
import ru.growerhub.backend.automation.contract.AutomationData.SetScenariosEnabledRequest;
import ru.growerhub.backend.automation.contract.ControlData.Manual;
import ru.growerhub.backend.automation.engine.AutomationWorker;
import ru.growerhub.backend.automation.engine.ControlWorker;
import ru.growerhub.backend.automation.engine.RuleWorker;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.demo.DemoFacade;
import ru.growerhub.backend.demo.contract.DemoData;
import ru.growerhub.backend.demo.engine.DemoWorker;
import ru.growerhub.backend.mqtt.MqttPublisher;
import ru.growerhub.backend.user.UserFacade;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;
import ru.growerhub.backend.zigbee.ZigbeeFacade;

@SpringBootTest(properties = {
        "MQTT_HOST=", "demo.enabled=true", "demo.history-days=1", "demo.history-step-minutes=360",
        "demo.max-guest-spaces=100", "demo.max-active-spaces=100", "demo.max-creates-per-hour=100",
        "spring.datasource.url=jdbc:h2:mem:control_concurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=15000"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ControlConcurrencyIntegrationTest extends IntegrationTestBase {
    private static final Instant START = Instant.parse("2026-10-22T09:00:00Z");
    @SpyBean DemoFacade demo;
    @SpyBean UserFacade users;
    @Autowired UserRepository accounts;
    @Autowired AutomationFacade automation;
    @Autowired ZigbeeFacade zigbee;
    @Autowired JdbcTemplate jdbc;
    @MockBean MqttPublisher mqtt;
    @MockBean DemoWorker demoWorker;
    @MockBean AutomationWorker automationWorker;
    @MockBean RuleWorker ruleWorker;
    @MockBean ControlWorker controlWorker;

    @org.springframework.boot.test.context.TestConfiguration
    static class TimeConfig {
        @Bean @Primary Clock controlConcurrencyClock() { return Clock.fixed(START, ZoneOffset.UTC); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void resetAndClaimedSwitchCommandCompleteWithOneOwnerOrderAndKeepSavedGeneration(boolean saved) throws Exception {
        jdbc.update("MERGE INTO demo_capacity (id) KEY(id) VALUES (1)");
        DemoData.Space initial;
        if (saved) {
            var account = accounts.save(UserEntity.create(UUID.randomUUID() + "@concurrency.test", "Owner", "user", true,
                    LocalDateTime.ofInstant(START, ZoneOffset.UTC), LocalDateTime.ofInstant(START, ZoneOffset.UTC)));
            initial = demo.openSavedOrCreate(account.getId(), "ru", "Europe/Moscow");
        } else initial = demo.createGuest("ru", "Europe/Moscow", "control-concurrency-" + UUID.randomUUID());
        var owner = new AuthenticatedUser(initial.dataUserId(), "demo");
        var disabled = automation.setUserScenariosEnabled(owner, new SetScenariosEnabledRequest(false));
        assertThat(disabled.farms()).isNotEmpty().allSatisfy(f -> {
            assertThat(f.scenarios()).allMatch(s -> !s.enabled());
            assertThat(f.greenhouses()).allSatisfy(g -> assertThat(g.scenarios()).allMatch(s -> !s.enabled()));
        });
        var target = disabled.farms().getFirst().greenhouses().getFirst().slots().stream()
                .filter(r -> "EXHAUST_SWITCH".equals(r.role())).findFirst().orElseThrow();
        var coordinator = zigbee.getSimulationCoordinator(owner.id());
        var device = zigbee.getDevicesForUser(owner).stream()
                .filter(d -> target.zigbeeIeeeAddress().equals(d.device().ieeeAddress())).findFirst().orElseThrow();
        zigbee.setDeviceProperty(owner, coordinator.publicId(), target.zigbeeIeeeAddress(), target.commandProperty(), "OFF");
        automation.setManualControl(target.id(), new Manual(UUID.randomUUID(), "ON", 60), owner);
        var command = automation.claimControlCommand(owner.id());
        assertThat(command).isNotNull();
        clearInvocations(demo, mqtt);

        var resetBeforeOwner = new CountDownLatch(1);
        var commandOwnsUser = new CountDownLatch(1);
        var firstResetLock = new AtomicBoolean();
        var firstCommandLock = new AtomicBoolean();
        var armed = new AtomicBoolean(true);
        var operation = new ThreadLocal<String>();
        doAnswer(call -> {
            if (!armed.get() || !owner.id().equals(call.getArgument(0))) return call.callRealMethod();
            if ("reset".equals(operation.get()) && firstResetLock.compareAndSet(false, true)) {
                resetBeforeOwner.countDown();
                assertThat(commandOwnsUser.await(20, TimeUnit.SECONDS)).as("command acquired the actual owner row").isTrue();
                return call.callRealMethod();
            }
            Object result = call.callRealMethod();
            if ("command".equals(operation.get()) && firstCommandLock.compareAndSet(false, true)) commandOwnsUser.countDown();
            return result;
        }).when(users).lockDemoOwner(owner.id());

        List<Thread> ownThreads = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(2, task -> {
            var thread = new Thread(task, "growerhub-control-reset-" + ownThreads.size());
            ownThreads.add(thread);
            return thread;
        });
        Future<DemoData.Space> reset = null;
        Future<?> execution = null;
        try {
            reset = executor.submit(() -> {
                operation.set("reset");
                try { return demo.reset(initial.id(), initial.generation()); }
                finally { operation.remove(); }
            });
            assertThat(resetBeforeOwner.await(20, TimeUnit.SECONDS)).as("reset reached its first owner boundary").isTrue();
            execution = executor.submit(() -> {
                operation.set("command");
                try { automation.executeControlCommand(command); }
                finally { operation.remove(); }
            });
            execution.get(45, TimeUnit.SECONDS);
            var replacement = reset.get(45, TimeUnit.SECONDS);
            assertThat(firstCommandLock).isTrue();
            assertThat(firstResetLock).isTrue();
            assertThat(replacement.id()).isEqualTo(initial.id());
            assertThat(replacement.dataUserId()).isEqualTo(initial.dataUserId());
            assertThat(replacement.generation()).isEqualTo(initial.generation() + 1);
            assertThat(replacement.saved()).isEqualTo(saved);
            assertThat(demo.authorize(replacement.id(), replacement.generation(), false).saved()).isEqualTo(saved);
            assertThatThrownBy(() -> demo.authorize(initial.id(), initial.generation(), false)).isInstanceOf(DomainException.class);
            automation.reconcileControls(owner.id());
            var fresh = automation.getFarmsOverview(owner);
            assertThat(fresh.farms()).hasSize(1);
            assertThat(fresh.farms().getFirst().greenhouses()).hasSize(4);
            assertThat(fresh.farms().getFirst().greenhouses()).allSatisfy(g ->
                    assertThat(g.slots()).noneMatch(r -> target.id().equals(r.id())));
            assertThat(automation.listControls(owner)).allSatisfy(c -> assertThat(c.demands()).isEmpty());
            assertThatThrownBy(() -> automation.executeControlCommand(command)).isInstanceOf(DomainException.class);
            verify(demo, times(1)).commandZigbee(coordinator.baseTopic(), device.device().friendlyName(), Map.of("state", "ON"));
            verifyNoInteractions(mqtt);
        } finally {
            armed.set(false);
            resetBeforeOwner.countDown();
            commandOwnsUser.countDown();
            if (execution != null && !execution.isDone()) execution.cancel(true);
            if (reset != null && !reset.isDone()) reset.cancel(true);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).as("owned concurrency threads stopped").isTrue();
            assertThat(ownThreads).allMatch(t -> !t.isAlive());
        }
    }
}
