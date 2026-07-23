package ru.growerhub.backend.pump;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.device.contract.DeviceShadowState;
import ru.growerhub.backend.pump.contract.PumpAck;
import ru.growerhub.backend.pump.contract.PumpHistoryPoint;
import ru.growerhub.backend.pump.contract.PumpRebootResult;
import ru.growerhub.backend.pump.contract.PumpStartResult;
import ru.growerhub.backend.pump.contract.PumpStatusResult;
import ru.growerhub.backend.pump.contract.PumpStopResult;
import ru.growerhub.backend.pump.engine.PumpBindingService;
import ru.growerhub.backend.pump.engine.PumpQueryService;
import ru.growerhub.backend.pump.engine.PumpService;
import ru.growerhub.backend.pump.engine.PumpStateHistoryService;
import ru.growerhub.backend.pump.engine.PumpSessionService;
import ru.growerhub.backend.pump.engine.PumpWateringService;
import ru.growerhub.backend.pump.contract.PumpView;
import ru.growerhub.backend.pump.contract.PumpSessionData;

@Service
public class PumpFacade {
    private final PumpBindingService bindingService;
    private final PumpWateringService wateringService;
    private final PumpQueryService queryService;
    private final PumpService pumpService;
    private final PumpStateHistoryService stateHistoryService;
    private final PumpSessionService sessionService;

    public PumpFacade(
            PumpBindingService bindingService,
            PumpWateringService wateringService,
            PumpQueryService queryService,
            PumpService pumpService,
            PumpStateHistoryService stateHistoryService,
            PumpSessionService sessionService
    ) {
        this.bindingService = bindingService;
        this.wateringService = wateringService;
        this.queryService = queryService;
        this.pumpService = pumpService;
        this.stateHistoryService = stateHistoryService;
        this.sessionService = sessionService;
    }

    @Transactional(readOnly = true)
    public PumpSessionData.Defaults sessionDefaults() {
        return sessionService.defaults();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = RuntimeException.class)
    public PumpSessionData.View startSession(PumpSessionData.Start request, AuthenticatedUser user) {
        return sessionService.start(request, user);
    }

    @Transactional
    public PumpSessionData.View stopSession(Integer pumpId, AuthenticatedUser user) {
        return sessionService.stop(pumpId, user);
    }

    @Transactional(readOnly = true)
    public PumpSessionData.View currentSession(Integer pumpId) {
        return sessionService.current(pumpId);
    }

    @Transactional(readOnly = true)
    public PumpSessionData.Page listSessions(Integer pumpId, int limit, Long beforeId) {
        return sessionService.listSessions(pumpId, limit, beforeId);
    }

    @Transactional(readOnly = true)
    public PumpSessionData.BoxStatistics boxStatistics(Integer boxId, String range, int limit, Long beforeId) {
        return sessionService.boxStatistics(boxId, range, limit, beforeId);
    }

    @Transactional(readOnly = true)
    public PumpSessionData.View lastCompletedSessionForBox(Integer boxId) {
        return sessionService.lastCompletedForBox(boxId);
    }

    @Transactional(readOnly = true)
    public List<PumpSessionData.Probe> listActiveSessionProbes() {
        return sessionService.listActiveProbes();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PumpSessionData.View advanceSession(
            Long sessionId,
            PumpSessionData.LeakProbe probe,
            LocalDateTime now
    ) {
        return sessionService.advance(sessionId, probe, now);
    }

    @Transactional
    public void syncAutomationBindings(Integer pumpId, List<PumpSessionData.BoxTarget> targets) {
        bindingService.syncAutomationBindings(pumpId, targets);
    }

    @Transactional
    public void updateBindings(Integer pumpId, List<PumpBindingItem> items, AuthenticatedUser user) {
        List<PumpBindingService.PumpBindingItem> mapped = items != null
                ? items.stream()
                .map(item -> new PumpBindingService.PumpBindingItem(
                        item.plantId(),
                        item.rateMlPerHour()
                ))
                .toList()
                : null;
        bindingService.updateBindings(pumpId, mapped, user);
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public PumpStartResult start(Integer pumpId, PumpWateringRequest request, AuthenticatedUser user) {
        return wateringService.start(
                pumpId,
                new PumpWateringService.PumpWateringRequest(
                        request.durationS(),
                        request.waterVolumeL(),
                        request.ph(),
                        request.fertilizersPerLiter()
                ),
                user
        );
    }

    @Transactional
    public PumpStopResult stop(Integer pumpId, AuthenticatedUser user) {
        return wateringService.stop(pumpId, user);
    }

    @Transactional
    public PumpRebootResult reboot(Integer pumpId, AuthenticatedUser user) {
        return wateringService.reboot(pumpId, user);
    }

    @Transactional
    public PumpStatusResult status(Integer pumpId, AuthenticatedUser user) {
        return wateringService.status(pumpId, user);
    }

    @Transactional(readOnly = true)
    public PumpAck getAck(String correlationId) {
        return wateringService.getAck(correlationId);
    }

    @Transactional
    public void finalizeWateringByDeviceId(String deviceId, LocalDateTime now) {
        wateringService.finalizeWateringByDeviceId(deviceId, now);
    }

    @Transactional
    public void recordStateByDeviceId(Integer devicePk, DeviceShadowState state, LocalDateTime now) {
        stateHistoryService.record(devicePk, state, now);
    }

    @Transactional(readOnly = true)
    public List<PumpHistoryPoint> getHistory(Integer pumpId, Integer hours, AuthenticatedUser user) {
        return stateHistoryService.getHistory(pumpId, hours, user);
    }

    @Transactional(readOnly = true)
    public LocalDateTime getOldestHistoryTimestamp() {
        return stateHistoryService.getOldestTimestamp();
    }

    @Transactional
    public int compactHistoryDay(LocalDateTime fromTs, LocalDateTime toTs) {
        return stateHistoryService.compactDay(fromTs, toTs);
    }

    @Transactional(readOnly = true)
    public List<PumpView> listByDeviceId(Integer deviceId, DeviceShadowState state) {
        return queryService.listByDevice(deviceId, state);
    }

    @Transactional(readOnly = true)
    public List<PumpView> listByPlantId(Integer plantId) {
        return queryService.listByPlantId(plantId);
    }

    @Transactional(readOnly = true)
    public List<PumpView> listByPlantIdLight(Integer plantId) {
        return queryService.listByPlantIdLight(plantId);
    }

    @Transactional
    public void ensureDefaultPump(Integer deviceId) {
        pumpService.ensureDefaultPump(deviceId);
    }

    @Transactional
    public void deleteByDeviceId(Integer deviceId) {
        pumpService.deleteAllByDeviceId(deviceId);
    }

    public record PumpBindingItem(Integer plantId, Integer rateMlPerHour) {
    }

    public record PumpWateringRequest(Integer durationS, Double waterVolumeL, Double ph, String fertilizersPerLiter) {
    }
}
