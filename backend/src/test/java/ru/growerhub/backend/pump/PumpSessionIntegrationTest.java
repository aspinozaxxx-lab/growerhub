package ru.growerhub.backend.pump;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import ru.growerhub.backend.IntegrationTestBase;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.device.jpa.DeviceEntity;
import ru.growerhub.backend.device.jpa.DeviceRepository;
import ru.growerhub.backend.device.engine.DeviceShadowStore;
import ru.growerhub.backend.journal.jpa.PlantJournalWateringDetailsEntity;
import ru.growerhub.backend.journal.jpa.PlantJournalWateringDetailsRepository;
import ru.growerhub.backend.plant.jpa.PlantEntity;
import ru.growerhub.backend.plant.jpa.PlantMetricSampleRepository;
import ru.growerhub.backend.plant.jpa.PlantRepository;
import ru.growerhub.backend.pump.contract.PumpAck;
import ru.growerhub.backend.pump.contract.PumpCommandGateway;
import ru.growerhub.backend.pump.contract.PumpSessionData;
import ru.growerhub.backend.pump.engine.PumpService;
import ru.growerhub.backend.pump.jpa.PumpEntity;
import ru.growerhub.backend.pump.jpa.PumpRepository;
import ru.growerhub.backend.user.jpa.UserEntity;
import ru.growerhub.backend.user.jpa.UserRepository;

@SpringBootTest(properties = {
        "automation.workerPeriodMs=3600000",
        "automation.wateringWorkerPeriodMs=3600000"
})
class PumpSessionIntegrationTest extends IntegrationTestBase {
    private static final int BOX_ID = 701;

    @Autowired
    private PumpFacade pumpFacade;

    @Autowired
    private PumpService pumpService;

    @Autowired
    private PumpRepository pumpRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private PlantRepository plantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlantJournalWateringDetailsRepository wateringDetailsRepository;

    @Autowired
    private PlantMetricSampleRepository metricRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private PumpCommandGateway commandGateway;

    @SpyBean
    private DeviceShadowStore shadowStore;

    @BeforeEach
    void setUp() {
        reset(commandGateway);
        when(commandGateway.getAck(any())).thenReturn(null);
        clearDatabase();
    }

    @Test
    void pulseCountsOnlyRunTimeAndWritesSessionJournalAndStatistics() {
        Fixture fixture = fixture("pulse", true);
        PumpSessionData.View started = start(
                fixture,
                PumpSessionData.MODE_TIMED,
                2,
                null,
                true,
                1,
                1,
                3600,
                List.of()
        );

        PumpSessionData.View pulseStopping = advance(started, 2, true, true, List.of());
        assertEquals(PumpSessionData.PHASE_STOPPING, pulseStopping.phase());
        PumpSessionData.View pause = advance(pulseStopping, 1, true, false, List.of());
        assertEquals(PumpSessionData.PHASE_PAUSE, pause.phase());
        assertEquals(1, pause.activeDurationS());

        PumpSessionData.View secondRun = advance(pause, 2, true, false, List.of());
        assertEquals(PumpSessionData.PHASE_RUNNING, secondRun.phase());

        PumpSessionData.View stopping = advance(secondRun, 2, true, true, List.of());
        assertEquals(PumpSessionData.PHASE_STOPPING, stopping.phase());
        assertEquals(PumpSessionData.REASON_DURATION, stopping.completionReason());
        assertEquals(2, stopping.activeDurationS());

        PumpSessionData.View completed = advance(stopping, 1, true, false, List.of());
        assertEquals(PumpSessionData.PHASE_COMPLETED, completed.phase());
        assertNull(pumpFacade.currentSession(fixture.pump().getId()));

        List<PlantJournalWateringDetailsEntity> details = wateringDetailsRepository.findAll();
        assertEquals(1, details.size());
        assertEquals(completed.id(), details.get(0).getPumpSessionId());
        assertEquals(2, details.get(0).getDurationS());
        assertEquals(0.002, details.get(0).getWaterVolumeL());
        assertEquals(PumpSessionData.MODE_TIMED, details.get(0).getMode());
        assertEquals(PumpSessionData.REASON_DURATION, details.get(0).getCompletionReason());
        assertEquals(1, metricRepository.count());

        PumpSessionData.BoxStatistics statistics = pumpFacade.boxStatistics(BOX_ID, "day", 20, null);
        assertEquals(1, statistics.sessionCount());
        assertEquals(2, statistics.activeDurationS());
        assertEquals(0.002, statistics.knownVolumeL());
        assertFalse(statistics.partialVolume());
        assertEquals(1L, statistics.modeCounts().get(PumpSessionData.MODE_TIMED));
        assertEquals(1L, statistics.reasonCounts().get(PumpSessionData.REASON_DURATION));

        PumpSessionData.View repeated = advance(completed, 30, true, false, List.of());
        assertEquals(PumpSessionData.PHASE_COMPLETED, repeated.phase());
        assertEquals(1, wateringDetailsRepository.count());
    }

    @Test
    void leakStopsTimedRunAndUntilLeakPause() {
        Fixture timedFixture = fixture("timed-leak", true);
        PumpSessionData.LeakTarget leak = leak(false);
        PumpSessionData.View timed = start(
                timedFixture,
                PumpSessionData.MODE_TIMED,
                10,
                null,
                false,
                null,
                null,
                3600,
                List.of(leak)
        );
        PumpSessionData.View timedStopping = advance(timed, 2, true, true, List.of(leakState(true)));
        assertEquals(PumpSessionData.PHASE_STOPPING, timedStopping.phase());
        assertEquals(PumpSessionData.REASON_LEAK, timedStopping.completionReason());
        PumpSessionData.View timedDone = advance(timedStopping, 1, true, false, List.of(leakState(true)));
        assertEquals(PumpSessionData.PHASE_COMPLETED, timedDone.phase());

        Fixture pauseFixture = fixture("pause-leak", true);
        PumpSessionData.View untilLeak = start(
                pauseFixture,
                PumpSessionData.MODE_UNTIL_LEAK,
                null,
                10,
                true,
                1,
                10,
                3600,
                List.of(leak(false))
        );
        PumpSessionData.View pulseStopping = advance(untilLeak, 2, true, true, List.of(leakState(false)));
        PumpSessionData.View pause = advance(pulseStopping, 1, true, false, List.of(leakState(false)));
        assertEquals(PumpSessionData.PHASE_PAUSE, pause.phase());
        PumpSessionData.View pauseDone = advance(pause, 1, true, false, List.of(leakState(true)));
        assertEquals(PumpSessionData.PHASE_COMPLETED, pauseDone.phase());
        assertEquals(1, pauseDone.activeDurationS());
        assertEquals(PumpSessionData.REASON_LEAK, pauseDone.completionReason());
        assertEquals(2, wateringDetailsRepository.count());
    }

    @Test
    void ambiguousInitialStartErrorKeepsSlotUntilConfirmedOffAndNeverJournals() {
        Fixture publishFailure = fixture("publish-error", true);
        doThrow(new DomainException("unavailable", "publisher unavailable"))
                .when(commandGateway)
                .publishStart(any(), any(), any(), any());

        assertThrows(DomainException.class, () -> start(
                publishFailure,
                PumpSessionData.MODE_TIMED,
                10,
                null,
                false,
                null,
                null,
                3600,
                List.of()
        ));
        verify(commandGateway, times(1)).publishStop(any(), any(), any());
        PumpSessionData.View stopping = pumpFacade.currentSession(publishFailure.pump().getId());
        assertEquals(PumpSessionData.PHASE_STOPPING, stopping.phase());
        assertEquals(PumpSessionData.REASON_COMMAND_ERROR, stopping.completionReason());
        assertThrows(DomainException.class, () -> start(
                publishFailure,
                PumpSessionData.MODE_TIMED,
                10,
                null,
                false,
                null,
                null,
                3600,
                List.of()
        ));
        PumpSessionData.View failed = advance(stopping, 1, true, false, List.of());
        assertEquals(PumpSessionData.PHASE_FAILED, failed.phase());
        assertEquals(PumpSessionData.REASON_COMMAND_ERROR, failed.completionReason());
        assertEquals(0, wateringDetailsRepository.count());

        reset(commandGateway);
        when(commandGateway.getAck(any())).thenReturn(null);
        Fixture negativeAck = fixture("negative-ack", true);
        PumpSessionData.View started = start(
                negativeAck,
                PumpSessionData.MODE_TIMED,
                10,
                null,
                false,
                null,
                null,
                3600,
                List.of()
        );
        when(commandGateway.getAck(started.correlationId()))
                .thenReturn(new PumpAck(started.correlationId(), "declined", "relay error", "idle"));
        PumpSessionData.View rejected = advance(started, 0, true, false, List.of());
        assertEquals(PumpSessionData.PHASE_FAILED, rejected.phase());
        assertEquals(PumpSessionData.REASON_COMMAND_ERROR, rejected.completionReason());
        assertEquals(0, rejected.activeDurationS());
        assertEquals(0, wateringDetailsRepository.count());
    }

    @Test
    void ambiguousPulseResumeErrorStopsImmediatelyAndKeepsCompletedRunTime() {
        Fixture fixture = fixture("pulse-start-error", true);
        PumpSessionData.View started = start(
                fixture,
                PumpSessionData.MODE_TIMED,
                4,
                null,
                true,
                2,
                1,
                3600,
                List.of()
        );
        PumpSessionData.View pulseStopping = advance(started, 3, true, true, List.of());
        PumpSessionData.View pause = advance(pulseStopping, 1, true, false, List.of());
        assertEquals(PumpSessionData.PHASE_PAUSE, pause.phase());
        assertEquals(2, pause.activeDurationS());
        doThrow(new DomainException("unavailable", "publisher unavailable"))
                .when(commandGateway)
                .publishStart(any(), any(), any(), any());

        PumpSessionData.View compensated = advance(pause, 2, true, false, List.of());

        assertEquals(PumpSessionData.PHASE_STOPPING, compensated.phase());
        assertEquals(PumpSessionData.REASON_COMMAND_ERROR, compensated.completionReason());
        assertEquals(2, compensated.activeDurationS());
        verify(commandGateway, times(2)).publishStop(any(), any(), any());
        PumpSessionData.View failed = advance(compensated, 1, true, false, List.of());
        assertEquals(PumpSessionData.PHASE_FAILED, failed.phase());
        assertEquals(2, failed.activeDurationS());
        assertEquals(0.002, wateringDetailsRepository.findAll().get(0).getWaterVolumeL());
    }

    @Test
    void publishedStartIsCompensatedWhenShadowUpdateFails() {
        Fixture fixture = fixture("start-compensation", true);
        doThrow(new DomainException("unavailable", "shadow unavailable"))
                .when(shadowStore)
                .updateFromStateAndPersist(any(), any(), any());

        assertThrows(DomainException.class, () -> start(
                fixture,
                PumpSessionData.MODE_TIMED,
                10,
                null,
                false,
                null,
                null,
                3600,
                List.of()
        ));

        verify(commandGateway, times(1)).publishStart(any(), any(), any(), any());
        verify(commandGateway, times(1)).publishStop(any(), any(), any());
        PumpSessionData.View stopping = pumpFacade.currentSession(fixture.pump().getId());
        assertEquals(PumpSessionData.PHASE_STOPPING, stopping.phase());
        assertEquals(PumpSessionData.REASON_COMMAND_ERROR, stopping.completionReason());
    }

    @Test
    void pulseRestartIsCompensatedWhenShadowUpdateFails() {
        Fixture fixture = fixture("pulse-compensation", true);
        PumpSessionData.View started = start(
                fixture,
                PumpSessionData.MODE_TIMED,
                2,
                null,
                true,
                1,
                1,
                3600,
                List.of()
        );
        PumpSessionData.View pulseStopping = advance(started, 2, true, true, List.of());
        PumpSessionData.View pause = advance(pulseStopping, 1, true, false, List.of());
        assertEquals(PumpSessionData.PHASE_PAUSE, pause.phase());
        doThrow(new DomainException("unavailable", "shadow unavailable"))
                .when(shadowStore)
                .updateFromStateAndPersist(any(), any(), any());

        PumpSessionData.View compensated = advance(pause, 2, true, false, List.of());

        assertEquals(PumpSessionData.PHASE_STOPPING, compensated.phase());
        assertEquals(PumpSessionData.REASON_COMMAND_ERROR, compensated.completionReason());
        verify(commandGateway, times(2)).publishStart(any(), any(), any(), any());
        verify(commandGateway, times(2)).publishStop(any(), any(), any());
    }

    @Test
    void pulseStopFailureDoesNotCountTheFinishedSegmentTwice() {
        Fixture fixture = fixture("pulse-stop-error", true);
        PumpSessionData.View started = start(
                fixture,
                PumpSessionData.MODE_TIMED,
                4,
                null,
                true,
                2,
                1,
                3600,
                List.of()
        );
        doThrow(new DomainException("unavailable", "stop publisher unavailable"))
                .when(commandGateway)
                .publishStop(any(), any(), any());

        PumpSessionData.View stopping = advance(started, 3, true, true, List.of());

        assertEquals(PumpSessionData.PHASE_STOPPING, stopping.phase());
        assertEquals(PumpSessionData.REASON_COMMAND_ERROR, stopping.completionReason());
        assertEquals(2, stopping.activeDurationS());

        reset(commandGateway);
        when(commandGateway.getAck(any())).thenReturn(null);
        PumpSessionData.View failed = advance(stopping, 1, true, false, List.of());
        assertEquals(PumpSessionData.PHASE_FAILED, failed.phase());
        assertEquals(2, failed.activeDurationS());
        assertEquals(0.002, wateringDetailsRepository.findAll().get(0).getWaterVolumeL());
    }

    @Test
    void startedSessionSurvivesRollbackOfTheCallingTransaction() {
        Fixture fixture = fixture("outer-rollback", true);

        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            start(
                    fixture,
                    PumpSessionData.MODE_TIMED,
                    10,
                    null,
                    false,
                    null,
                    null,
                    3600,
                    List.of()
            );
            throw new IllegalStateException("rollback after published start");
        }));

        PumpSessionData.View active = pumpFacade.currentSession(fixture.pump().getId());
        assertEquals(PumpSessionData.PHASE_RUNNING, active.phase());
        verify(commandGateway, times(1)).publishStart(any(), any(), any(), any());
    }

    @Test
    void stopAckConfirmsSlotButOfflineUnknownKeepsItBlocked() {
        Fixture fixture = fixture("stop-ack", true);
        PumpSessionData.View started = start(
                fixture,
                PumpSessionData.MODE_TIMED,
                10,
                null,
                false,
                null,
                null,
                3600,
                List.of()
        );
        PumpSessionData.View stopping = pumpFacade.stopSession(fixture.pump().getId(), fixture.user());
        verify(commandGateway, times(1)).publishStop(any(), any(), any());
        PumpSessionData.View withoutConfirmation = pumpFacade.advanceSession(
                stopping.id(),
                new PumpSessionData.LeakProbe(null, null, null, List.of()),
                stopping.phaseStartedAt().plusSeconds(1)
        );
        assertEquals(PumpSessionData.PHASE_STOPPING, withoutConfirmation.phase());
        assertEquals(PumpSessionData.STATUS_ACTIVE, withoutConfirmation.status());
        verify(commandGateway, times(1)).publishStop(any(), any(), any());
        PumpSessionData.View firstRetry = advance(stopping, 16, true, true, List.of());
        verify(commandGateway, times(2)).publishStop(any(), any(), any());
        PumpSessionData.View beforeSecondRetry = advance(firstRetry, 30, true, true, List.of());
        verify(commandGateway, times(2)).publishStop(any(), any(), any());
        PumpSessionData.View secondRetry = advance(beforeSecondRetry, 31, true, true, List.of());
        verify(commandGateway, times(3)).publishStop(any(), any(), any());
        PumpSessionData.View stillBlocked = advance(secondRetry, 32, false, false, List.of());
        assertEquals(PumpSessionData.PHASE_STOPPING, stillBlocked.phase());
        assertEquals(PumpSessionData.STATUS_ACTIVE, stillBlocked.status());
        assertEquals(PumpSessionData.REASON_DEVICE_OFFLINE, stillBlocked.completionReason());
        assertTrue(pumpFacade.currentSession(fixture.pump().getId()) != null);

        when(commandGateway.getAck(stillBlocked.correlationId()))
                .thenReturn(new PumpAck(stillBlocked.correlationId(), "accepted", null, "idle"));
        PumpSessionData.View confirmed = advance(stillBlocked, 1, false, false, List.of());
        assertEquals(PumpSessionData.PHASE_FAILED, confirmed.phase());
        assertNull(pumpFacade.currentSession(fixture.pump().getId()));
    }

    @Test
    void stopRetryPreservesCausalCompletionReason() {
        Fixture fixture = fixture("stop-reason", true);
        PumpSessionData.View started = start(
                fixture,
                PumpSessionData.MODE_TIMED,
                1,
                null,
                false,
                null,
                null,
                3600,
                List.of()
        );
        PumpSessionData.View stopping = advance(started, 2, true, true, List.of());
        assertEquals(PumpSessionData.REASON_DURATION, stopping.completionReason());

        PumpSessionData.View retried = advance(stopping, 16, true, true, List.of());
        assertEquals(PumpSessionData.REASON_DURATION, retried.completionReason());
        verify(commandGateway, times(2)).publishStop(any(), any(), any());

        PumpSessionData.View completed = advance(retried, 1, true, false, List.of());
        assertEquals(PumpSessionData.PHASE_COMPLETED, completed.phase());
        assertEquals(PumpSessionData.REASON_DURATION, completed.completionReason());
        assertEquals(PumpSessionData.REASON_DURATION,
                wateringDetailsRepository.findAll().get(0).getCompletionReason());
        assertEquals(1L, pumpFacade.boxStatistics(BOX_ID, "day", 10, null)
                .reasonCounts().get(PumpSessionData.REASON_DURATION));
    }

    @Test
    void nullableRateSkipsVolumeMetricAndDeviceSlotCoversAllChannels() {
        Fixture fixture = fixture("nullable-rate", true);
        PumpEntity secondPump = PumpEntity.create();
        secondPump.setDeviceId(fixture.device().getId());
        secondPump.setChannel(1);
        secondPump = pumpRepository.save(secondPump);

        PumpSessionData.View started = start(
                fixture,
                PumpSessionData.MODE_TIMED,
                1,
                null,
                false,
                null,
                null,
                null,
                List.of()
        );
        PumpEntity otherChannel = secondPump;
        assertThrows(DomainException.class, () -> pumpFacade.startSession(new PumpSessionData.Start(
                otherChannel.getId(),
                PumpSessionData.SOURCE_ADMIN_MANUAL,
                PumpSessionData.MODE_TIMED,
                1,
                null,
                false,
                null,
                null,
                targets(fixture, null, List.of()),
                null,
                null,
                null
        ), fixture.user()));

        PumpSessionData.View stopping = advance(started, 2, true, true, List.of());
        PumpSessionData.View done = advance(stopping, 1, true, false, List.of());
        assertEquals(PumpSessionData.PHASE_COMPLETED, done.phase());
        PlantJournalWateringDetailsEntity details = wateringDetailsRepository.findAll().get(0);
        assertNull(details.getWaterVolumeL());
        assertEquals(0, metricRepository.count());
        assertTrue(done.partialVolume());
        assertNull(done.knownVolumeL());
    }

    @Test
    void offlineDeviceCannotStart() {
        Fixture fixture = fixture("offline", false);
        assertThrows(DomainException.class, () -> start(
                fixture,
                PumpSessionData.MODE_TIMED,
                10,
                null,
                false,
                null,
                null,
                3600,
                List.of()
        ));
        assertTrue(pumpFacade.listSessions(fixture.pump().getId(), 20, null).items().isEmpty());
    }

    @Test
    void wetStartIsRejectedForBothModesAndUnavailableSensorStopsUntilLeak() {
        Fixture fixture = fixture("wet-start", true);
        for (String mode : List.of(PumpSessionData.MODE_TIMED, PumpSessionData.MODE_UNTIL_LEAK)) {
            assertThrows(DomainException.class, () -> start(
                    fixture,
                    mode,
                    PumpSessionData.MODE_TIMED.equals(mode) ? 10 : null,
                    PumpSessionData.MODE_UNTIL_LEAK.equals(mode) ? 10 : null,
                    false,
                    null,
                    null,
                    3600,
                    List.of(new PumpSessionData.LeakTarget(
                            "wet",
                            2,
                            "ZIGBEE_DEVICE",
                            "0x2",
                            "water_leak",
                            "Wet sensor",
                            false,
                            true
                    ))
            ));
        }

        PumpSessionData.View active = start(
                fixture,
                PumpSessionData.MODE_UNTIL_LEAK,
                null,
                10,
                false,
                null,
                null,
                3600,
                List.of(leak(false))
        );
        PumpSessionData.View stopping = advance(
                active,
                1,
                true,
                true,
                List.of(new PumpSessionData.LeakState("leak:1", false, false))
        );
        assertEquals(PumpSessionData.PHASE_STOPPING, stopping.phase());
        assertEquals(PumpSessionData.REASON_SENSOR_UNAVAILABLE, stopping.completionReason());
        PumpSessionData.View failed = advance(stopping, 1, true, false, List.of());
        assertEquals(PumpSessionData.PHASE_FAILED, failed.phase());
    }

    @Test
    void untilLeakLimitAndZeroSecondManualStopAreJournaled() {
        Fixture limitFixture = fixture("limit", true);
        PumpSessionData.View untilLeak = start(
                limitFixture,
                PumpSessionData.MODE_UNTIL_LEAK,
                null,
                2,
                false,
                null,
                null,
                3600,
                List.of(leak(false))
        );
        PumpSessionData.View limitStopping = advance(untilLeak, 3, true, true, List.of(leakState(false)));
        assertEquals(PumpSessionData.REASON_LIMIT, limitStopping.completionReason());
        PumpSessionData.View limitDone = advance(limitStopping, 1, true, false, List.of(leakState(false)));
        assertEquals(PumpSessionData.PHASE_COMPLETED, limitDone.phase());
        assertEquals(2, limitDone.activeDurationS());

        Fixture zeroFixture = fixture("zero-stop", true);
        PumpSessionData.View zeroStarted = start(
                zeroFixture,
                PumpSessionData.MODE_TIMED,
                10,
                null,
                false,
                null,
                null,
                3600,
                List.of()
        );
        PumpSessionData.View zeroStopping = pumpFacade.stopSession(zeroFixture.pump().getId(), zeroFixture.user());
        PumpSessionData.View zeroDone = pumpFacade.advanceSession(
                zeroStopping.id(),
                new PumpSessionData.LeakProbe(true, false, zeroStopping.phaseStartedAt().plusSeconds(1), List.of()),
                zeroStopping.phaseStartedAt().plusSeconds(1)
        );
        assertEquals(PumpSessionData.PHASE_COMPLETED, zeroDone.phase());
        assertEquals(0, zeroDone.activeDurationS());
        PlantJournalWateringDetailsEntity zeroDetails = wateringDetailsRepository.findAll().stream()
                .filter(item -> zeroDone.id().equals(item.getPumpSessionId()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, zeroDetails.getDurationS());
        assertEquals(0.0, zeroDetails.getWaterVolumeL());
    }

    @Test
    void activeProbeAndSnapshotStayStableAfterPlantMutation() {
        Fixture fixture = fixture("snapshot", true);
        PumpSessionData.View started = start(
                fixture,
                PumpSessionData.MODE_TIMED,
                10,
                null,
                false,
                null,
                null,
                4321,
                List.of()
        );
        assertTrue(pumpFacade.listActiveSessionProbes().stream()
                .anyMatch(probe -> started.id().equals(probe.sessionId())));

        fixture.plant().setName("Changed current plant");
        plantRepository.save(fixture.plant());
        PumpSessionData.View snapshot = pumpFacade.currentSession(fixture.pump().getId());
        assertEquals("Box snapshot", snapshot.boxes().get(0).boxName());
        assertEquals("Plant snapshot", snapshot.boxes().get(0).plants().get(0).plantName());
        assertEquals(4321, snapshot.boxes().get(0).plants().get(0).rateMlPerHour());
    }

    @Test
    void manualStopDuringPulseStopCancelsResume() {
        Fixture fixture = fixture("pulse-manual", true);
        PumpSessionData.View started = start(
                fixture,
                PumpSessionData.MODE_TIMED,
                10,
                null,
                true,
                1,
                10,
                3600,
                List.of()
        );
        PumpSessionData.View pulseStopping = advance(started, 2, true, true, List.of());
        assertEquals(PumpSessionData.PHASE_STOPPING, pulseStopping.phase());
        PumpSessionData.View manualStopping = pumpFacade.stopSession(fixture.pump().getId(), fixture.user());
        assertEquals(PumpSessionData.REASON_MANUAL, manualStopping.completionReason());
        PumpSessionData.View done = advance(manualStopping, 1, true, false, List.of());
        assertEquals(PumpSessionData.PHASE_COMPLETED, done.phase());
        assertEquals(PumpSessionData.REASON_MANUAL, done.completionReason());
    }

    private PumpSessionData.View start(
            Fixture fixture,
            String mode,
            Integer durationS,
            Integer maxActiveDurationS,
            boolean pulse,
            Integer pulseRunS,
            Integer pulsePauseS,
            Integer rate,
            List<PumpSessionData.LeakTarget> leaks
    ) {
        return pumpFacade.startSession(new PumpSessionData.Start(
                fixture.pump().getId(),
                PumpSessionData.SOURCE_ADMIN_MANUAL,
                mode,
                durationS,
                maxActiveDurationS,
                pulse,
                pulseRunS,
                pulsePauseS,
                targets(fixture, rate, leaks),
                null,
                null,
                null
        ), fixture.user());
    }

    private List<PumpSessionData.BoxTarget> targets(
            Fixture fixture,
            Integer rate,
            List<PumpSessionData.LeakTarget> leaks
    ) {
        return List.of(new PumpSessionData.BoxTarget(
                BOX_ID,
                "Box snapshot",
                91,
                "Room snapshot",
                List.of(new PumpSessionData.PlantTarget(
                        fixture.plant().getId(),
                        fixture.plant().getName(),
                        rate,
                        fixture.userEntity().getId()
                )),
                leaks
        ));
    }

    private PumpSessionData.View advance(
            PumpSessionData.View current,
            int seconds,
            boolean online,
            boolean running,
            List<PumpSessionData.LeakState> leaks
    ) {
        return pumpFacade.advanceSession(
                current.id(),
                new PumpSessionData.LeakProbe(
                        online,
                        running,
                        current.phaseStartedAt().plusSeconds(seconds),
                        leaks
                ),
                current.phaseStartedAt().plusSeconds(seconds)
        );
    }

    private PumpSessionData.LeakTarget leak(boolean triggered) {
        return new PumpSessionData.LeakTarget(
                "leak:1",
                1,
                "ZIGBEE_DEVICE",
                "0x1",
                "water_leak",
                "Drain sensor",
                true,
                triggered
        );
    }

    private PumpSessionData.LeakState leakState(boolean triggered) {
        return new PumpSessionData.LeakState("leak:1", true, triggered);
    }

    private Fixture fixture(String suffix, boolean online) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        UserEntity user = userRepository.save(UserEntity.create(
                suffix + "@example.com",
                suffix,
                "admin",
                true,
                now,
                now
        ));
        DeviceEntity device = DeviceEntity.create();
        device.setDeviceId("session-" + suffix);
        device.setName("Session " + suffix);
        device.setUserId(user.getId());
        device.setLastSeen(online ? now : null);
        device = deviceRepository.save(device);
        PumpEntity pump = pumpService.ensureDefaultPump(device.getId());
        PlantEntity plant = PlantEntity.create();
        plant.setUserId(user.getId());
        plant.setName("Plant " + suffix);
        plant.setPlantedAt(now);
        plant = plantRepository.save(plant);
        return new Fixture(user, device, pump, plant, new AuthenticatedUser(user.getId(), "admin"));
    }

    private void clearDatabase() {
        jdbcTemplate.update("DELETE FROM plant_metric_samples");
        jdbcTemplate.update("DELETE FROM plant_journal_watering_details");
        jdbcTemplate.update("DELETE FROM plant_journal_photos");
        jdbcTemplate.update("DELETE FROM plant_journal_entries");
        jdbcTemplate.update("DELETE FROM pump_watering_session_leaks");
        jdbcTemplate.update("DELETE FROM pump_watering_session_plants");
        jdbcTemplate.update("DELETE FROM pump_watering_session_boxes");
        jdbcTemplate.update("DELETE FROM pump_watering_sessions");
        jdbcTemplate.update("DELETE FROM pump_plant_bindings");
        jdbcTemplate.update("DELETE FROM pumps");
        jdbcTemplate.update("DELETE FROM plants");
        jdbcTemplate.update("DELETE FROM device_state_last");
        jdbcTemplate.update("DELETE FROM devices");
        jdbcTemplate.update("DELETE FROM user_auth_identities");
        jdbcTemplate.update("DELETE FROM user_refresh_tokens");
        jdbcTemplate.update("DELETE FROM users");
    }

    private record Fixture(
            UserEntity userEntity,
            DeviceEntity device,
            PumpEntity pump,
            PlantEntity plant,
            AuthenticatedUser user
    ) {
    }
}
