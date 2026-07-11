package ru.growerhub.backend.pump.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ru.growerhub.backend.common.config.AutomationSettings;
import ru.growerhub.backend.common.config.pump.PumpWateringSettings;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.device.DeviceFacade;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.device.contract.DeviceSummary;
import ru.growerhub.backend.journal.JournalFacade;
import ru.growerhub.backend.plant.PlantFacade;
import ru.growerhub.backend.plant.contract.PlantInfo;
import ru.growerhub.backend.pump.contract.PumpCommandGateway;
import ru.growerhub.backend.pump.contract.PumpAck;
import ru.growerhub.backend.pump.contract.PumpRunningStatusProvider;
import ru.growerhub.backend.pump.contract.PumpSessionData;
import ru.growerhub.backend.pump.jpa.PumpEntity;
import ru.growerhub.backend.pump.jpa.PumpPlantBindingEntity;
import ru.growerhub.backend.pump.jpa.PumpPlantBindingRepository;
import ru.growerhub.backend.pump.jpa.PumpRepository;
import ru.growerhub.backend.pump.jpa.PumpWateringSessionBoxEntity;
import ru.growerhub.backend.pump.jpa.PumpWateringSessionBoxRepository;
import ru.growerhub.backend.pump.jpa.PumpWateringSessionEntity;
import ru.growerhub.backend.pump.jpa.PumpWateringSessionLeakEntity;
import ru.growerhub.backend.pump.jpa.PumpWateringSessionLeakRepository;
import ru.growerhub.backend.pump.jpa.PumpWateringSessionPlantEntity;
import ru.growerhub.backend.pump.jpa.PumpWateringSessionPlantRepository;
import ru.growerhub.backend.pump.jpa.PumpWateringSessionRepository;

@Service
public class PumpSessionService {
    private static final Set<String> SOURCES = Set.of(
            PumpSessionData.SOURCE_ADMIN_MANUAL,
            PumpSessionData.SOURCE_AUTOMATION,
            PumpSessionData.SOURCE_USER_MANUAL
    );
    private static final Set<String> MODES = Set.of(
            PumpSessionData.MODE_TIMED,
            PumpSessionData.MODE_UNTIL_LEAK
    );

    private final PumpRepository pumpRepository;
    private final PumpPlantBindingRepository bindingRepository;
    private final PumpWateringSessionRepository sessionRepository;
    private final PumpWateringSessionBoxRepository boxRepository;
    private final PumpWateringSessionPlantRepository sessionPlantRepository;
    private final PumpWateringSessionLeakRepository leakRepository;
    private final DeviceFacade deviceFacade;
    private final PlantFacade plantFacade;
    private final JournalFacade journalFacade;
    private final PumpCommandGateway commandGateway;
    private final PumpRunningStatusProvider runningStatusProvider;
    private final PumpWateringSettings settings;
    private final AutomationSettings automationSettings;

    public PumpSessionService(
            PumpRepository pumpRepository,
            PumpPlantBindingRepository bindingRepository,
            PumpWateringSessionRepository sessionRepository,
            PumpWateringSessionBoxRepository boxRepository,
            PumpWateringSessionPlantRepository sessionPlantRepository,
            PumpWateringSessionLeakRepository leakRepository,
            @Lazy DeviceFacade deviceFacade,
            PlantFacade plantFacade,
            JournalFacade journalFacade,
            PumpCommandGateway commandGateway,
            PumpRunningStatusProvider runningStatusProvider,
            PumpWateringSettings settings,
            AutomationSettings automationSettings
    ) {
        this.pumpRepository = pumpRepository;
        this.bindingRepository = bindingRepository;
        this.sessionRepository = sessionRepository;
        this.boxRepository = boxRepository;
        this.sessionPlantRepository = sessionPlantRepository;
        this.leakRepository = leakRepository;
        this.deviceFacade = deviceFacade;
        this.plantFacade = plantFacade;
        this.journalFacade = journalFacade;
        this.commandGateway = commandGateway;
        this.runningStatusProvider = runningStatusProvider;
        this.settings = settings;
        this.automationSettings = automationSettings;
    }

    public PumpSessionData.Defaults defaults() {
        return new PumpSessionData.Defaults(
                settings.getDefaultTimedDurationS(),
                settings.getDefaultUntilLeakMaxActiveDurationS(),
                settings.getDefaultPulseRunS(),
                settings.getDefaultPulsePauseS()
        );
    }

    public PumpSessionData.View start(PumpSessionData.Start request, AuthenticatedUser user) {
        return startInternal(request, user, true, request != null ? request.waterVolumeL() : null);
    }

    public PumpSessionData.View startLegacy(
            Integer pumpId,
            int durationS,
            Double plannedWaterVolumeL,
            Double ph,
            String fertilizersPerLiter,
            AuthenticatedUser user
    ) {
        List<PumpSessionData.BoxTarget> targets = legacyTargets(pumpId);
        if (targets.isEmpty()) {
            throw new DomainException("bad_request", "nasos ne privyazan ni k odnomu rasteniyu");
        }
        PumpSessionData.Start request = new PumpSessionData.Start(
                pumpId,
                PumpSessionData.SOURCE_USER_MANUAL,
                PumpSessionData.MODE_TIMED,
                durationS,
                null,
                false,
                null,
                null,
                targets,
                plannedWaterVolumeL,
                ph,
                fertilizersPerLiter
        );
        return startInternal(request, user, true, plannedWaterVolumeL);
    }

    private PumpSessionData.View startInternal(
            PumpSessionData.Start request,
            AuthenticatedUser user,
            boolean requireOnline,
            Double plannedWaterVolumeL
    ) {
        if (request == null || request.pumpId() == null) {
            throw new DomainException("bad_request", "pump_id obyazatelen");
        }
        PumpEntity pump = requirePumpAccess(request.pumpId(), user);
        DeviceSummary device = requireDevice(pump);
        pumpRepository.lockAllByDeviceId(pump.getDeviceId());
        validateDeviceReady(device, pump, requireOnline);
        ResolvedStart resolved = resolveStart(request);
        validateTargets(resolved.mode(), resolved.boxes());
        if (sessionRepository.findByActiveDeviceKey(device.deviceId()).isPresent()) {
            throw new DomainException("conflict", "fizicheskoe ustrojstvo uzhe zanyato polivom");
        }

        LocalDateTime now = nowUtc();
        String correlationId = correlationId();
        PumpWateringSessionEntity session = PumpWateringSessionEntity.create();
        session.setPumpId(pump.getId());
        session.setDeviceId(device.id());
        session.setDeviceKey(device.deviceId());
        session.setActiveDeviceKey(device.deviceId());
        session.setChannel(pump.getChannel() != null ? pump.getChannel() : 0);
        session.setPumpLabel(pump.getLabel());
        session.setUserId(user != null && user.id() != null && user.id() > 0 ? user.id() : null);
        session.setSource(resolved.source());
        session.setMode(resolved.mode());
        session.setPhase(PumpSessionData.PHASE_RUNNING);
        session.setPlannedDurationS(resolved.durationS());
        session.setMaxActiveDurationS(resolved.maxActiveDurationS());
        session.setPulseEnabled(resolved.pulseEnabled());
        session.setPulseRunS(resolved.pulseRunS());
        session.setPulsePauseS(resolved.pulsePauseS());
        session.setActiveDurationS(0);
        session.setJournalEligible(true);
        session.setStartedAt(now);
        session.setPhaseStartedAt(now);
        session.setUpdatedAt(now);
        session.setCorrelationId(correlationId);
        session.setPlannedWaterVolumeL(plannedWaterVolumeL);
        session.setPh(request.ph());
        session.setFertilizersPerLiter(request.fertilizersPerLiter());
        sessionRepository.saveAndFlush(session);
        saveTargets(session, resolved.boxes());

        sessionRepository.flush();
        int commandDurationS = currentRunDurationS(session);
        boolean published = false;
        try {
            commandGateway.publishStart(device.deviceId(), correlationId, now, commandDurationS);
            published = true;
            session.setLastCommandAt(now);
            sessionRepository.saveAndFlush(session);
            updateDeviceManualState(session, commandDurationS, now, "running");
        } catch (RuntimeException ex) {
            if (!published) {
                session.setJournalEligible(false);
            }
            compensateStartAttempt(session, ex, now);
            throw ex;
        }
        return toView(session, now);
    }

    public PumpSessionData.View stop(Integer pumpId, AuthenticatedUser user) {
        PumpEntity pump = requirePumpAccess(pumpId, user);
        PumpWateringSessionEntity session = sessionRepository
                .findFirstByPumpIdAndActiveDeviceKeyIsNotNullOrderByIdDesc(pump.getId())
                .orElse(null);
        if (session == null) {
            return null;
        }
        session = sessionRepository.findByIdForUpdate(session.getId()).orElse(null);
        if (session == null || session.getActiveDeviceKey() == null) {
            return null;
        }
        LocalDateTime now = nowUtc();
        if (PumpSessionData.PHASE_STOPPING.equals(session.getPhase())) {
            if (session.getStoppingTargetPhase() != null) {
                session.setStoppingTargetPhase(null);
                session.setCompletionReason(PumpSessionData.REASON_MANUAL);
                session.setUpdatedAt(now);
                sessionRepository.save(session);
            }
        } else {
            boolean alreadyConfirmedOff = PumpSessionData.PHASE_PAUSE.equals(session.getPhase());
            if (alreadyConfirmedOff) {
                finishConfirmedPause(session, PumpSessionData.REASON_MANUAL, now);
            } else {
                enterStopping(session, PumpSessionData.REASON_MANUAL, now);
            }
        }
        return toView(session, now);
    }

    public PumpSessionData.View current(Integer pumpId) {
        if (pumpId == null) {
            return null;
        }
        PumpWateringSessionEntity session = sessionRepository
                .findFirstByPumpIdAndActiveDeviceKeyIsNotNullOrderByIdDesc(pumpId)
                .orElse(null);
        return session != null ? toView(session, nowUtc()) : null;
    }

    public PumpSessionData.Page listSessions(Integer pumpId, int limit, Long beforeId) {
        int pageSize = pageSize(limit);
        List<PumpWateringSessionEntity> sessions = sessionRepository.findPageByPumpId(
                pumpId,
                beforeId,
                PageRequest.of(0, pageSize)
        );
        LocalDateTime now = nowUtc();
        List<PumpSessionData.View> views = sessions.stream().map(item -> toView(item, now)).toList();
        Long nextBeforeId = views.size() == pageSize ? views.get(views.size() - 1).id() : null;
        return new PumpSessionData.Page(views, nextBeforeId);
    }

    public List<PumpSessionData.Probe> listActiveProbes() {
        List<PumpSessionData.Probe> result = new ArrayList<>();
        for (PumpWateringSessionEntity session : sessionRepository.findAllByActiveDeviceKeyIsNotNullOrderByIdAsc()) {
            result.add(new PumpSessionData.Probe(
                    session.getId(),
                    session.getPumpId(),
                    session.getDeviceKey(),
                    session.getMode(),
                    session.getPhase(),
                    leakTargets(session.getId())
            ));
        }
        return result;
    }

    public PumpSessionData.View advance(Long sessionId, PumpSessionData.LeakProbe probe, LocalDateTime requestedNow) {
        PumpWateringSessionEntity session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
        if (session == null) {
            return null;
        }
        LocalDateTime now = requestedNow != null ? requestedNow : nowUtc();
        if (session.getActiveDeviceKey() == null) {
            return toView(session, now);
        }
        PumpSessionData.LeakProbe resolvedProbe = resolveProbe(session, probe);
        PumpAck ack = commandGateway.getAck(session.getCorrelationId());
        if (PumpSessionData.PHASE_STOPPING.equals(session.getPhase())) {
            if (isStopConfirmed(ack)) {
                confirmStopTransition(session, now);
                return toView(session, now);
            }
            if (isNegativeAck(ack)) {
                session.setStoppingTargetPhase(null);
                session.setCompletionReason(PumpSessionData.REASON_COMMAND_ERROR);
                session.setErrorMessage(ackError(ack));
                session.setUpdatedAt(now);
                sessionRepository.save(session);
            }
            advanceStopping(session, resolvedProbe, now);
            return toView(session, now);
        }
        if (isNegativeAck(ack)) {
            rejectCurrentCommand(session, ack, now);
            if (Boolean.TRUE.equals(resolvedProbe.deviceOnline())
                    && Boolean.FALSE.equals(resolvedProbe.pumpRunning())) {
                finish(session, now);
            }
            return toView(session, now);
        }
        String safetyReason = safetyStopReason(session, resolvedProbe);
        if (safetyReason != null) {
            boolean alreadyConfirmedOff = PumpSessionData.PHASE_PAUSE.equals(session.getPhase());
            if (alreadyConfirmedOff) {
                finishConfirmedPause(session, safetyReason, now);
            } else {
                enterStopping(session, safetyReason, now);
            }
            return toView(session, now);
        }
        if (PumpSessionData.PHASE_PAUSE.equals(session.getPhase())) {
            advancePause(session, now);
        } else if (PumpSessionData.PHASE_RUNNING.equals(session.getPhase())) {
            advanceRunning(session, now);
        }
        return toView(session, now);
    }

    public PumpSessionData.View confirmStopped(Long sessionId, LocalDateTime now) {
        return advance(
                sessionId,
                new PumpSessionData.LeakProbe(Boolean.TRUE, Boolean.FALSE, List.of()),
                now
        );
    }

    public boolean finalizeReportedStopped(String deviceKey, LocalDateTime requestedNow) {
        if (deviceKey == null || deviceKey.isBlank()) {
            return false;
        }
        PumpWateringSessionEntity session = sessionRepository.findByActiveDeviceKey(deviceKey).orElse(null);
        if (session == null) {
            return false;
        }
        DeviceShadowState shadow = deviceFacade.getShadowState(deviceKey);
        DeviceShadowState.ManualWateringState manual = shadow != null ? shadow.manualWatering() : null;
        if (manual == null || "running".equalsIgnoreCase(manual.status())) {
            return true;
        }
        if (PumpSessionData.PHASE_STOPPING.equals(session.getPhase())) {
            confirmStopped(session.getId(), requestedNow != null ? requestedNow : nowUtc());
        }
        return true;
    }

    public PumpSessionData.View lastCompletedForBox(Integer boxId) {
        List<PumpWateringSessionBoxEntity> rows = boxRepository.findLastCompletedForBox(
                boxId,
                PageRequest.of(0, 1)
        );
        PumpWateringSessionBoxEntity box = rows.isEmpty() ? null : rows.get(0);
        return box != null ? toView(box.getSession(), nowUtc()) : null;
    }

    public PumpSessionData.BoxStatistics boxStatistics(Integer boxId, String range, int limit, Long beforeId) {
        TimeRange timeRange = timeRange(range);
        List<PumpWateringSessionBoxEntity> all = boxRepository
                .findAllByBoxIdAndSession_FinishedAtGreaterThanEqualAndSession_FinishedAtLessThan(
                        boxId,
                        timeRange.from(),
                        timeRange.to()
                );
        Map<Long, PumpWateringSessionEntity> distinct = new LinkedHashMap<>();
        for (PumpWateringSessionBoxEntity box : all) {
            distinct.put(box.getSession().getId(), box.getSession());
        }
        long activeDurationS = 0;
        double knownVolume = 0.0;
        boolean hasKnownVolume = false;
        boolean partialVolume = false;
        Map<String, Long> modeCounts = new HashMap<>();
        Map<String, Long> reasonCounts = new HashMap<>();
        for (PumpWateringSessionEntity session : distinct.values()) {
            activeDurationS += session.getActiveDurationS();
            modeCounts.merge(session.getMode(), 1L, Long::sum);
            if (session.getCompletionReason() != null) {
                reasonCounts.merge(statisticsReason(session.getCompletionReason()), 1L, Long::sum);
            }
            PumpWateringSessionBoxEntity sessionBox = findBox(session.getId(), boxId);
            if (sessionBox != null) {
                for (PumpWateringSessionPlantEntity plant : sessionPlantRepository.findAllBySessionBox_IdOrderById(sessionBox.getId())) {
                    if (plant.getWaterVolumeL() == null) {
                        partialVolume = true;
                    } else {
                        knownVolume += plant.getWaterVolumeL();
                        hasKnownVolume = true;
                    }
                }
            }
        }

        int pageSize = pageSize(limit);
        List<PumpWateringSessionBoxEntity> page = boxRepository.findPageByBoxId(
                boxId,
                timeRange.from(),
                timeRange.to(),
                beforeId,
                PageRequest.of(0, pageSize)
        );
        List<PumpSessionData.View> sessions = page.stream()
                .map(PumpWateringSessionBoxEntity::getSession)
                .map(item -> toView(item, nowUtc()))
                .toList();
        Long nextBeforeId = sessions.size() == pageSize ? sessions.get(sessions.size() - 1).id() : null;
        PumpWateringSessionBoxEntity activeBox = boxRepository
                .findFirstByBoxIdAndSession_ActiveDeviceKeyIsNotNullOrderBySession_IdDesc(boxId)
                .orElse(null);
        PumpSessionData.View active = activeBox != null ? toView(activeBox.getSession(), nowUtc()) : null;
        return new PumpSessionData.BoxStatistics(
                boxId,
                timeRange.name(),
                timeRange.from(),
                timeRange.to(),
                distinct.size(),
                activeDurationS,
                hasKnownVolume ? roundVolume(knownVolume) : null,
                partialVolume,
                Map.copyOf(modeCounts),
                Map.copyOf(reasonCounts),
                sessions,
                nextBeforeId,
                active
        );
    }

    public List<PumpSessionData.BoxTarget> legacyTargets(Integer pumpId) {
        List<PumpPlantBindingEntity> bindings = bindingRepository.findAllByPump_Id(pumpId);
        List<PumpSessionData.PlantTarget> plants = new ArrayList<>();
        for (PumpPlantBindingEntity binding : bindings) {
            PlantInfo plant = plantFacade.getPlantInfoById(binding.getPlantId());
            if (plant == null) {
                continue;
            }
            plants.add(new PumpSessionData.PlantTarget(
                    plant.id(),
                    plant.name(),
                    binding.getRateMlPerHour(),
                    plant.userId()
            ));
        }
        if (plants.isEmpty()) {
            return List.of();
        }
        return List.of(new PumpSessionData.BoxTarget(null, null, null, null, plants, List.of()));
    }

    private void advanceRunning(PumpWateringSessionEntity session, LocalDateTime now) {
        int elapsed = runningElapsedS(session, now);
        int segmentS = currentRunDurationS(session);
        if (elapsed < segmentS) {
            return;
        }
        session.setActiveDurationS(Math.min(targetActiveDurationS(session), session.getActiveDurationS() + segmentS));
        session.setUpdatedAt(now);
        if (session.getActiveDurationS() >= targetActiveDurationS(session)) {
            enterStopping(session, terminalTimeReason(session), now);
            return;
        }
        if (!session.isPulseEnabled()) {
            enterStopping(session, terminalTimeReason(session), now);
            return;
        }
        String correlationId = correlationId();
        try {
            commandGateway.publishStop(session.getDeviceKey(), correlationId, now);
            session.setCorrelationId(correlationId);
            session.setLastCommandAt(now);
            session.setPhase(PumpSessionData.PHASE_STOPPING);
            session.setStoppingTargetPhase(PumpSessionData.PHASE_PAUSE);
            session.setPhaseStartedAt(now);
            session.setUpdatedAt(now);
            sessionRepository.save(session);
        } catch (RuntimeException ex) {
            enterCommandErrorStoppingAfterCommittedRun(session, ex, now);
        }
    }

    private void advancePause(PumpWateringSessionEntity session, LocalDateTime now) {
        if (elapsedS(session.getPhaseStartedAt(), now) < session.getPulsePauseS()) {
            return;
        }
        int durationS = currentRunDurationS(session);
        String correlationId = correlationId();
        try {
            commandGateway.publishStart(session.getDeviceKey(), correlationId, now, durationS);
            session.setCorrelationId(correlationId);
            session.setLastCommandAt(now);
            session.setPhase(PumpSessionData.PHASE_RUNNING);
            session.setPhaseStartedAt(now);
            session.setUpdatedAt(now);
            sessionRepository.saveAndFlush(session);
            updateDeviceManualState(session, durationS, now, "running");
        } catch (RuntimeException ex) {
            compensateStartAttempt(session, ex, now);
        }
    }

    private void compensateStartAttempt(
            PumpWateringSessionEntity session,
            RuntimeException startError,
            LocalDateTime now
    ) {
        commitRunningTime(session, now);
        session.setPhase(PumpSessionData.PHASE_STOPPING);
        session.setStoppingTargetPhase(null);
        session.setPhaseStartedAt(now);
        session.setCompletionReason(PumpSessionData.REASON_COMMAND_ERROR);
        session.setErrorMessage(startError.getMessage());
        session.setUpdatedAt(now);
        String stopCorrelationId = correlationId();
        session.setCorrelationId(stopCorrelationId);
        session.setLastCommandAt(now);
        try {
            commandGateway.publishStop(session.getDeviceKey(), stopCorrelationId, now);
        } catch (RuntimeException stopError) {
            startError.addSuppressed(stopError);
        }
        try {
            sessionRepository.saveAndFlush(session);
        } catch (RuntimeException persistenceError) {
            startError.addSuppressed(persistenceError);
        }
    }

    private void advanceStopping(
            PumpWateringSessionEntity session,
            PumpSessionData.LeakProbe probe,
            LocalDateTime now
    ) {
        if (isFreshStopped(session, probe)) {
            confirmStopTransition(session, now);
            return;
        }
        if (Boolean.FALSE.equals(probe.deviceOnline())) {
            session.setStoppingTargetPhase(null);
            session.setCompletionReason(PumpSessionData.REASON_DEVICE_OFFLINE);
            session.setErrorMessage("ustrojstvo ne v seti, ostanovka ne podtverzhdena");
            session.setUpdatedAt(now);
            sessionRepository.save(session);
            return;
        }
        LocalDateTime lastAttemptAt = session.getLastCommandAt() != null
                ? session.getLastCommandAt()
                : session.getPhaseStartedAt();
        if (elapsedS(lastAttemptAt, now) < settings.getStoppingTimeoutS()) {
            return;
        }
        session.setStoppingTargetPhase(null);
        if (session.getErrorMessage() == null) {
            session.setErrorMessage("ostanovka nasosa ne podtverzhdena v timeout");
        }
        String correlationId = correlationId();
        try {
            commandGateway.publishStop(session.getDeviceKey(), correlationId, now);
            session.setCorrelationId(correlationId);
            session.setLastCommandAt(now);
            session.setUpdatedAt(now);
            sessionRepository.save(session);
        } catch (RuntimeException ex) {
            session.setLastCommandAt(now);
            session.setCompletionReason(PumpSessionData.REASON_COMMAND_ERROR);
            session.setErrorMessage(ex.getMessage());
            session.setUpdatedAt(now);
            sessionRepository.save(session);
        }
    }

    private void enterStopping(PumpWateringSessionEntity session, String reason, LocalDateTime now) {
        commitRunningTime(session, now);
        session.setPhase(PumpSessionData.PHASE_STOPPING);
        session.setStoppingTargetPhase(null);
        session.setPhaseStartedAt(now);
        session.setCompletionReason(reason);
        session.setUpdatedAt(now);
        String correlationId = correlationId();
        try {
            commandGateway.publishStop(session.getDeviceKey(), correlationId, now);
            session.setCorrelationId(correlationId);
            session.setLastCommandAt(now);
            sessionRepository.save(session);
        } catch (RuntimeException ex) {
            session.setLastCommandAt(now);
            session.setCompletionReason(PumpSessionData.REASON_COMMAND_ERROR);
            session.setErrorMessage(ex.getMessage());
            sessionRepository.save(session);
        }
    }

    private void enterCommandErrorStoppingAfterCommittedRun(
            PumpWateringSessionEntity session,
            RuntimeException ex,
            LocalDateTime now
    ) {
        session.setPhase(PumpSessionData.PHASE_STOPPING);
        session.setStoppingTargetPhase(null);
        session.setPhaseStartedAt(now);
        session.setCompletionReason(PumpSessionData.REASON_COMMAND_ERROR);
        session.setErrorMessage(ex.getMessage());
        session.setLastCommandAt(now);
        session.setUpdatedAt(now);
        sessionRepository.save(session);
    }

    private void rejectCurrentCommand(PumpWateringSessionEntity session, PumpAck ack, LocalDateTime now) {
        if (session.getActiveDurationS() == 0
                && PumpSessionData.PHASE_RUNNING.equals(session.getPhase())
                && session.getStartedAt().equals(session.getPhaseStartedAt())) {
            session.setJournalEligible(false);
        }
        session.setPhase(PumpSessionData.PHASE_STOPPING);
        session.setStoppingTargetPhase(null);
        session.setPhaseStartedAt(now);
        session.setCompletionReason(PumpSessionData.REASON_COMMAND_ERROR);
        session.setErrorMessage(ackError(ack));
        session.setUpdatedAt(now);
        String correlationId = correlationId();
        try {
            commandGateway.publishStop(session.getDeviceKey(), correlationId, now);
            session.setCorrelationId(correlationId);
            session.setLastCommandAt(now);
        } catch (RuntimeException ex) {
            session.setLastCommandAt(now);
            session.setErrorMessage(ex.getMessage());
        }
        sessionRepository.save(session);
    }

    private void confirmStopTransition(PumpWateringSessionEntity session, LocalDateTime now) {
        if (PumpSessionData.PHASE_PAUSE.equals(session.getStoppingTargetPhase())
                && session.getActiveDurationS() < targetActiveDurationS(session)) {
            session.setPhase(PumpSessionData.PHASE_PAUSE);
            session.setStoppingTargetPhase(null);
            session.setCompletionReason(null);
            session.setErrorMessage(null);
            session.setPhaseStartedAt(now);
            session.setUpdatedAt(now);
            sessionRepository.save(session);
            updateDeviceManualState(session, session.getPulsePauseS(), now, "pause");
            return;
        }
        finish(session, now);
    }

    private void finishConfirmedPause(PumpWateringSessionEntity session, String reason, LocalDateTime now) {
        session.setPhase(PumpSessionData.PHASE_STOPPING);
        session.setStoppingTargetPhase(null);
        session.setCompletionReason(reason);
        session.setPhaseStartedAt(now);
        session.setUpdatedAt(now);
        sessionRepository.save(session);
        finish(session, now);
    }

    private void finish(PumpWateringSessionEntity session, LocalDateTime now) {
        if (session.getActiveDeviceKey() == null) {
            return;
        }
        commitRunningTime(session, now);
        int durationS = session.getActiveDurationS();
        List<PumpWateringSessionPlantEntity> plants = sessionPlantRepository.findAllBySession_IdOrderById(session.getId());
        List<JournalFacade.SessionWateringTarget> journalTargets = new ArrayList<>();
        for (PumpWateringSessionPlantEntity plant : plants) {
            Double volumeL = calculateVolume(plant.getRateMlPerHour(), durationS);
            plant.setDurationS(durationS);
            plant.setWaterVolumeL(volumeL);
            sessionPlantRepository.save(plant);
            if (session.isJournalEligible()) {
                journalTargets.add(new JournalFacade.SessionWateringTarget(
                        session.getId(),
                        plant.getPlantId(),
                        plant.getOwnerId(),
                        durationS,
                        volumeL,
                        session.getMode(),
                        session.getCompletionReason()
                ));
            }
        }
        if (session.isJournalEligible()) {
            journalFacade.createSessionWateringEntries(
                    journalTargets,
                    session.getStartedAt(),
                    session.getPh(),
                    session.getFertilizersPerLiter()
            );
            for (PumpWateringSessionPlantEntity plant : plants) {
                plantFacade.recordWateringEvent(plant.getPlantId(), plant.getWaterVolumeL(), session.getStartedAt());
            }
        }
        session.setPhase(isFailureReason(session.getCompletionReason())
                ? PumpSessionData.PHASE_FAILED
                : PumpSessionData.PHASE_COMPLETED);
        session.setActiveDeviceKey(null);
        session.setStoppingTargetPhase(null);
        session.setFinishedAt(now);
        session.setPhaseStartedAt(now);
        session.setUpdatedAt(now);
        sessionRepository.save(session);
        updateDeviceManualState(session, 0, now, "completed");
    }

    private PumpSessionData.LeakProbe resolveProbe(
            PumpWateringSessionEntity session,
            PumpSessionData.LeakProbe probe
    ) {
        DeviceSummary device = deviceFacade.getDeviceSummary(session.getDeviceId());
        Boolean online = probe != null && probe.deviceOnline() != null
                ? probe.deviceOnline()
                : device != null ? device.isOnline() : false;
        Boolean running = probe != null && probe.pumpRunning() != null
                ? probe.pumpRunning()
                : runningStatusProvider.isPumpRunning(session.getDeviceKey(), session.getChannel());
        LocalDateTime observedAt = probe != null && probe.pumpObservedAt() != null
                ? probe.pumpObservedAt()
                : device != null ? device.lastSeen() : null;
        List<PumpSessionData.LeakState> states = probe != null && probe.leakStates() != null
                ? probe.leakStates()
                : List.of();
        return new PumpSessionData.LeakProbe(online, running, observedAt, states);
    }

    private String safetyStopReason(PumpWateringSessionEntity session, PumpSessionData.LeakProbe probe) {
        boolean available = false;
        for (PumpSessionData.LeakState state : probe.leakStates()) {
            if (state != null && state.available()) {
                available = true;
                if (state.triggered()) {
                    return PumpSessionData.REASON_LEAK;
                }
            }
        }
        if (Boolean.FALSE.equals(probe.deviceOnline())) {
            return PumpSessionData.REASON_DEVICE_OFFLINE;
        }
        if (PumpSessionData.MODE_UNTIL_LEAK.equals(session.getMode()) && !available) {
            return PumpSessionData.REASON_SENSOR_UNAVAILABLE;
        }
        return null;
    }

    private void commitRunningTime(PumpWateringSessionEntity session, LocalDateTime now) {
        if (!PumpSessionData.PHASE_RUNNING.equals(session.getPhase())) {
            return;
        }
        int elapsed = Math.min(runningElapsedS(session, now), currentRunDurationS(session));
        session.setActiveDurationS(Math.min(
                targetActiveDurationS(session),
                session.getActiveDurationS() + elapsed
        ));
    }

    private void saveTargets(PumpWateringSessionEntity session, List<PumpSessionData.BoxTarget> targets) {
        for (PumpSessionData.BoxTarget target : targets) {
            PumpWateringSessionBoxEntity box = PumpWateringSessionBoxEntity.create();
            box.setSession(session);
            box.setBoxId(target.boxId());
            box.setBoxName(target.boxName());
            box.setRoomId(target.roomId());
            box.setRoomName(target.roomName());
            boxRepository.save(box);
            List<PumpSessionData.PlantTarget> targetPlants = target.plants() != null ? target.plants() : List.of();
            for (PumpSessionData.PlantTarget targetPlant : targetPlants) {
                PumpWateringSessionPlantEntity plant = PumpWateringSessionPlantEntity.create();
                plant.setSession(session);
                plant.setSessionBox(box);
                plant.setPlantId(targetPlant.plantId());
                plant.setPlantName(targetPlant.plantName());
                plant.setOwnerId(targetPlant.ownerId());
                plant.setRateMlPerHour(targetPlant.rateMlPerHour());
                sessionPlantRepository.save(plant);
            }
            List<PumpSessionData.LeakTarget> leaks = target.leakSensors() != null ? target.leakSensors() : List.of();
            for (PumpSessionData.LeakTarget targetLeak : leaks) {
                if (targetLeak == null || targetLeak.reference() == null || targetLeak.reference().isBlank()) {
                    continue;
                }
                PumpWateringSessionLeakEntity leak = PumpWateringSessionLeakEntity.create();
                leak.setSession(session);
                leak.setSessionBox(box);
                leak.setReference(targetLeak.reference());
                leak.setResourceBindingId(targetLeak.resourceBindingId());
                leak.setSourceType(targetLeak.sourceType());
                leak.setExternalId(targetLeak.externalId());
                leak.setProperty(targetLeak.property());
                leak.setLabel(targetLeak.label());
                leak.setAvailableAtStart(targetLeak.available());
                leak.setTriggeredAtStart(targetLeak.triggered());
                leakRepository.save(leak);
            }
        }
    }

    private ResolvedStart resolveStart(PumpSessionData.Start request) {
        String source = request.source() != null ? request.source() : PumpSessionData.SOURCE_ADMIN_MANUAL;
        if (!SOURCES.contains(source)) {
            throw new DomainException("bad_request", "neizvestnyi istochnik poliva");
        }
        String mode = request.mode() != null ? request.mode() : PumpSessionData.MODE_TIMED;
        if (!MODES.contains(mode)) {
            throw new DomainException("bad_request", "neizvestnyi rezhim poliva");
        }
        Integer durationS = null;
        Integer maxActiveDurationS = null;
        if (PumpSessionData.MODE_TIMED.equals(mode)) {
            Integer requestedDurationS = request.durationS();
            if (requestedDurationS == null && request.waterVolumeL() != null) {
                requestedDurationS = durationFromVolume(request.boxes(), request.waterVolumeL());
            }
            durationS = positiveOrDefault(requestedDurationS, settings.getDefaultTimedDurationS(), "duration_s");
            validateMax(durationS, "duration_s");
        } else {
            maxActiveDurationS = positiveOrDefault(
                    request.maxActiveDurationS(),
                    settings.getDefaultUntilLeakMaxActiveDurationS(),
                    "max_active_duration_s"
            );
            validateMax(maxActiveDurationS, "max_active_duration_s");
        }
        boolean pulseEnabled = Boolean.TRUE.equals(request.pulseEnabled());
        Integer pulseRunS = pulseEnabled
                ? positiveOrDefault(request.pulseRunS(), settings.getDefaultPulseRunS(), "pulse_run_s")
                : null;
        Integer pulsePauseS = pulseEnabled
                ? positiveOrDefault(request.pulsePauseS(), settings.getDefaultPulsePauseS(), "pulse_pause_s")
                : null;
        List<PumpSessionData.BoxTarget> boxes = request.boxes() != null ? request.boxes() : List.of();
        return new ResolvedStart(source, mode, durationS, maxActiveDurationS, pulseEnabled, pulseRunS, pulsePauseS, boxes);
    }

    private void validateTargets(String mode, List<PumpSessionData.BoxTarget> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            throw new DomainException("bad_request", "nasos ne privyazan ni k odnomu boksu");
        }
        Set<Integer> plantIds = new HashSet<>();
        boolean hasPlant = false;
        boolean hasAvailableLeak = false;
        boolean leakTriggered = false;
        for (PumpSessionData.BoxTarget box : boxes) {
            if (box == null) {
                continue;
            }
            if (box.plants() != null) {
                for (PumpSessionData.PlantTarget plant : box.plants()) {
                    if (plant == null || plant.plantId() == null) {
                        continue;
                    }
                    if (!plantIds.add(plant.plantId())) {
                        throw new DomainException("bad_request", "rastenie ukazano v sessii bolee odnogo raza");
                    }
                    if (plant.rateMlPerHour() != null && plant.rateMlPerHour() <= 0) {
                        throw new DomainException("bad_request", "rate_ml_per_hour dolzhen byt' > 0");
                    }
                    hasPlant = true;
                }
            }
            if (box.leakSensors() != null) {
                for (PumpSessionData.LeakTarget leak : box.leakSensors()) {
                    if (leak == null || leak.reference() == null || leak.reference().isBlank()) {
                        continue;
                    }
                    hasAvailableLeak |= Boolean.TRUE.equals(leak.available());
                    leakTriggered |= Boolean.TRUE.equals(leak.triggered());
                }
            }
        }
        if (!hasPlant) {
            throw new DomainException("bad_request", "v boksah net rastenij dlya poliva");
        }
        if (leakTriggered) {
            throw new DomainException("conflict", "datchik protechki uzhe srabotal");
        }
        if (PumpSessionData.MODE_UNTIL_LEAK.equals(mode)) {
            if (!hasAvailableLeak) {
                throw new DomainException("bad_request", "dlya poliva do protechki net dostupnogo datchika");
            }
        }
    }

    private void validateDeviceReady(DeviceSummary device, PumpEntity pump, boolean requireOnline) {
        if (requireOnline && !Boolean.TRUE.equals(device.isOnline())) {
            throw new DomainException("conflict", "ustrojstvo nasosa ne v seti");
        }
        int channel = pump.getChannel() != null ? pump.getChannel() : 0;
        if (runningStatusProvider.isPumpRunning(device.deviceId(), channel)) {
            throw new DomainException("conflict", "nasos uzhe vklyuchen");
        }
    }

    private PumpEntity requirePumpAccess(Integer pumpId, AuthenticatedUser user) {
        PumpEntity pump = pumpRepository.findById(pumpId).orElse(null);
        if (pump == null) {
            throw new DomainException("not_found", "nasos ne naiden");
        }
        if (user == null) {
            throw new DomainException("forbidden", "nedostatochno prav dlya etogo nasosa");
        }
        if (!user.isAdmin()) {
            DeviceSummary summary = deviceFacade.getDeviceSummary(pump.getDeviceId());
            Integer ownerId = summary != null ? summary.userId() : null;
            if (ownerId == null || !ownerId.equals(user.id())) {
                throw new DomainException("forbidden", "nedostatochno prav dlya etogo nasosa");
            }
        }
        return pump;
    }

    private DeviceSummary requireDevice(PumpEntity pump) {
        DeviceSummary summary = pump != null ? deviceFacade.getDeviceSummary(pump.getDeviceId()) : null;
        if (summary == null || summary.deviceId() == null || summary.deviceId().isBlank()) {
            throw new DomainException("not_found", "device not found for pump");
        }
        return summary;
    }

    private PumpSessionData.View lastSession(Integer pumpId) {
        PumpSessionData.Page page = listSessions(pumpId, 1, null);
        return page.items().isEmpty() ? null : page.items().get(0);
    }

    private PumpSessionData.View toView(PumpWateringSessionEntity session, LocalDateTime now) {
        int activeDurationS = currentActiveDurationS(session, now);
        int targetS = targetActiveDurationS(session);
        Integer remainingActiveS = session.getActiveDeviceKey() != null ? Math.max(0, targetS - activeDurationS) : 0;
        Integer phaseRemainingS = phaseRemainingS(session, now);
        List<PumpSessionData.BoxSnapshot> boxes = new ArrayList<>();
        boolean partialVolume = false;
        boolean hasKnownVolume = false;
        double knownVolume = 0.0;
        for (PumpWateringSessionBoxEntity box : boxRepository.findAllBySession_IdOrderById(session.getId())) {
            List<PumpSessionData.PlantSnapshot> plants = new ArrayList<>();
            for (PumpWateringSessionPlantEntity plant : sessionPlantRepository.findAllBySessionBox_IdOrderById(box.getId())) {
                Double volume = plant.getWaterVolumeL();
                if (session.getActiveDeviceKey() != null) {
                    volume = calculateVolume(plant.getRateMlPerHour(), activeDurationS);
                }
                if (volume == null) {
                    partialVolume = true;
                } else {
                    hasKnownVolume = true;
                    knownVolume += volume;
                }
                plants.add(new PumpSessionData.PlantSnapshot(
                        plant.getPlantId(),
                        plant.getPlantName(),
                        plant.getRateMlPerHour(),
                        plant.getOwnerId(),
                        session.getActiveDeviceKey() != null ? Integer.valueOf(activeDurationS) : plant.getDurationS(),
                        volume
                ));
            }
            List<PumpSessionData.LeakTarget> leaks = leakRepository.findAllBySessionBox_IdOrderById(box.getId())
                    .stream()
                    .map(this::toLeakTarget)
                    .toList();
            boxes.add(new PumpSessionData.BoxSnapshot(
                    box.getBoxId(),
                    box.getBoxName(),
                    box.getRoomId(),
                    box.getRoomName(),
                    plants,
                    leaks
            ));
        }
        String status = session.getActiveDeviceKey() != null
                ? PumpSessionData.STATUS_ACTIVE
                : PumpSessionData.STATUS_TERMINAL;
        return new PumpSessionData.View(
                session.getId(),
                session.getPumpId(),
                session.getDeviceId(),
                session.getDeviceKey(),
                session.getChannel(),
                session.getPumpLabel(),
                session.getUserId(),
                session.getSource(),
                session.getMode(),
                status,
                session.getPhase(),
                session.getPlannedDurationS(),
                session.getMaxActiveDurationS(),
                session.isPulseEnabled(),
                session.getPulseRunS(),
                session.getPulsePauseS(),
                activeDurationS,
                remainingActiveS,
                phaseRemainingS,
                hasKnownVolume ? roundVolume(knownVolume) : null,
                partialVolume,
                !partialVolume,
                session.getStartedAt(),
                session.getPhaseStartedAt(),
                session.getFinishedAt(),
                session.getUpdatedAt(),
                session.getCorrelationId(),
                session.getCompletionReason(),
                session.getErrorMessage(),
                boxes
        );
    }

    private List<PumpSessionData.LeakTarget> leakTargets(Long sessionId) {
        return leakRepository.findAllBySession_IdOrderById(sessionId).stream().map(this::toLeakTarget).toList();
    }

    private PumpSessionData.LeakTarget toLeakTarget(PumpWateringSessionLeakEntity leak) {
        return new PumpSessionData.LeakTarget(
                leak.getReference(),
                leak.getResourceBindingId(),
                leak.getSourceType(),
                leak.getExternalId(),
                leak.getProperty(),
                leak.getLabel(),
                leak.getAvailableAtStart(),
                leak.getTriggeredAtStart()
        );
    }

    private PumpWateringSessionBoxEntity findBox(Long sessionId, Integer boxId) {
        for (PumpWateringSessionBoxEntity box : boxRepository.findAllBySession_IdOrderById(sessionId)) {
            if (boxId != null && boxId.equals(box.getBoxId())) {
                return box;
            }
        }
        return null;
    }

    private void updateDeviceManualState(
            PumpWateringSessionEntity session,
            Integer durationS,
            LocalDateTime now,
            String status
    ) {
        if (session.getDeviceKey() == null) {
            return;
        }
        DeviceShadowState.ManualWateringState state = new DeviceShadowState.ManualWateringState(
                status,
                durationS,
                session.getStartedAt(),
                durationS,
                session.getCorrelationId(),
                session.getPumpId(),
                session.getPlannedWaterVolumeL(),
                session.getPh(),
                session.getFertilizersPerLiter(),
                session.getActiveDeviceKey() == null ? session.getCorrelationId() : null
        );
        deviceFacade.updateManualWateringState(session.getDeviceKey(), state, now);
    }

    private int currentActiveDurationS(PumpWateringSessionEntity session, LocalDateTime now) {
        if (!PumpSessionData.PHASE_RUNNING.equals(session.getPhase()) || session.getActiveDeviceKey() == null) {
            return session.getActiveDurationS();
        }
        int elapsed = Math.min(runningElapsedS(session, now), currentRunDurationS(session));
        return Math.min(targetActiveDurationS(session), session.getActiveDurationS() + elapsed);
    }

    private int runningElapsedS(PumpWateringSessionEntity session, LocalDateTime now) {
        return elapsedS(session.getPhaseStartedAt(), now);
    }

    private int currentRunDurationS(PumpWateringSessionEntity session) {
        int remaining = Math.max(1, targetActiveDurationS(session) - session.getActiveDurationS());
        if (!session.isPulseEnabled()) {
            return remaining;
        }
        return Math.min(remaining, session.getPulseRunS());
    }

    private int targetActiveDurationS(PumpWateringSessionEntity session) {
        Integer value = PumpSessionData.MODE_UNTIL_LEAK.equals(session.getMode())
                ? session.getMaxActiveDurationS()
                : session.getPlannedDurationS();
        return value != null ? value : 0;
    }

    private Integer phaseRemainingS(PumpWateringSessionEntity session, LocalDateTime now) {
        if (session.getActiveDeviceKey() == null || PumpSessionData.PHASE_STOPPING.equals(session.getPhase())) {
            return null;
        }
        int phaseDuration = PumpSessionData.PHASE_PAUSE.equals(session.getPhase())
                ? session.getPulsePauseS()
                : currentRunDurationS(session);
        return Math.max(0, phaseDuration - elapsedS(session.getPhaseStartedAt(), now));
    }

    private int elapsedS(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || to.isBefore(from)) {
            return 0;
        }
        long seconds = Duration.between(from, to).getSeconds();
        return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
    }

    private String terminalTimeReason(PumpWateringSessionEntity session) {
        return PumpSessionData.MODE_UNTIL_LEAK.equals(session.getMode())
                ? PumpSessionData.REASON_LIMIT
                : PumpSessionData.REASON_DURATION;
    }

    private boolean isFailureReason(String reason) {
        return PumpSessionData.REASON_COMMAND_ERROR.equals(reason)
                || PumpSessionData.REASON_DEVICE_OFFLINE.equals(reason)
                || PumpSessionData.REASON_SENSOR_UNAVAILABLE.equals(reason)
                || PumpSessionData.REASON_RECOVERY.equals(reason);
    }

    private boolean isFreshStopped(PumpWateringSessionEntity session, PumpSessionData.LeakProbe probe) {
        return Boolean.TRUE.equals(probe.deviceOnline())
                && Boolean.FALSE.equals(probe.pumpRunning())
                && probe.pumpObservedAt() != null
                && session.getPhaseStartedAt() != null
                && !probe.pumpObservedAt().isBefore(session.getPhaseStartedAt());
    }

    private String statisticsReason(String reason) {
        return isFailureReason(reason) ? "error" : reason;
    }

    private boolean isNegativeAck(PumpAck ack) {
        if (ack == null || ack.result() == null) {
            return false;
        }
        return "error".equalsIgnoreCase(ack.result())
                || "declined".equalsIgnoreCase(ack.result());
    }

    private boolean isStopConfirmed(PumpAck ack) {
        if (ack == null || !"accepted".equalsIgnoreCase(ack.result()) || ack.status() == null) {
            return false;
        }
        return "idle".equalsIgnoreCase(ack.status())
                || "stopped".equalsIgnoreCase(ack.status())
                || "off".equalsIgnoreCase(ack.status())
                || "completed".equalsIgnoreCase(ack.status());
    }

    private String ackError(PumpAck ack) {
        if (ack == null) {
            return "komanda otklonena ustrojstvom";
        }
        return ack.reason() != null && !ack.reason().isBlank()
                ? ack.reason()
                : "komanda otklonena ustrojstvom";
    }

    private Double calculateVolume(Integer rateMlPerHour, int durationS) {
        if (rateMlPerHour == null) {
            return null;
        }
        double liters = rateMlPerHour.doubleValue() * durationS / 3_600_000.0;
        return roundVolume(liters);
    }

    private Double roundVolume(double volumeL) {
        return BigDecimal.valueOf(volumeL).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    private int positiveOrDefault(Integer value, int fallback, String field) {
        int resolved = value != null ? value : fallback;
        if (resolved <= 0) {
            throw new DomainException("bad_request", field + " dolzhen byt' > 0");
        }
        return resolved;
    }

    private int durationFromVolume(List<PumpSessionData.BoxTarget> boxes, Double volumeL) {
        if (volumeL == null || volumeL <= 0.0) {
            throw new DomainException("bad_request", "water_volume_l dolzhen byt' > 0");
        }
        double totalRate = 0.0;
        int plantCount = 0;
        if (boxes != null) {
            for (PumpSessionData.BoxTarget box : boxes) {
                if (box == null || box.plants() == null) {
                    continue;
                }
                for (PumpSessionData.PlantTarget plant : box.plants()) {
                    if (plant == null || plant.plantId() == null) {
                        continue;
                    }
                    totalRate += plant.rateMlPerHour() != null
                            ? plant.rateMlPerHour()
                            : settings.getDefaultRateMlPerHour();
                    plantCount++;
                }
            }
        }
        if (plantCount == 0 || totalRate <= 0.0) {
            throw new DomainException("bad_request", "net skorosti dlya rascheta dlitel'nosti poliva");
        }
        double averageRateLph = totalRate / plantCount / 1000.0;
        return (int) Math.max(1, Math.ceil(volumeL / averageRateLph * 3600.0));
    }

    private void validateMax(int value, String field) {
        if (value > settings.getMaxActiveDurationS()) {
            throw new DomainException("bad_request", field + " prevyshaet dopustimyj limit");
        }
    }

    private int pageSize(int requested) {
        int positive = requested > 0 ? requested : settings.getSessionPageMax();
        return Math.min(positive, settings.getSessionPageMax());
    }

    private TimeRange timeRange(String requestedRange) {
        String range = requestedRange != null ? requestedRange : "day";
        ZoneId zone;
        try {
            zone = ZoneId.of(automationSettings.getTimezone());
        } catch (RuntimeException ex) {
            throw new DomainException("bad_request", "nekorrektnyj automation.timezone");
        }
        LocalDate today = ZonedDateTime.now(ZoneOffset.UTC).withZoneSameInstant(zone).toLocalDate();
        LocalDate fromDate;
        LocalDate toDate;
        switch (range) {
            case "day" -> {
                fromDate = today;
                toDate = today.plusDays(1);
            }
            case "week" -> {
                fromDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                toDate = fromDate.plusWeeks(1);
            }
            case "month" -> {
                fromDate = today.withDayOfMonth(1);
                toDate = fromDate.plusMonths(1);
            }
            default -> throw new DomainException("bad_request", "range dolzhen byt' day, week ili month");
        }
        LocalDateTime from = fromDate.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime to = toDate.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        return new TimeRange(range, from, to);
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private String correlationId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private record ResolvedStart(
            String source,
            String mode,
            Integer durationS,
            Integer maxActiveDurationS,
            boolean pulseEnabled,
            Integer pulseRunS,
            Integer pulsePauseS,
            List<PumpSessionData.BoxTarget> boxes
    ) {
    }

    private record TimeRange(String name, LocalDateTime from, LocalDateTime to) {
    }
}
