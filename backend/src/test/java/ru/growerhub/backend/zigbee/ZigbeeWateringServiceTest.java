package ru.growerhub.backend.zigbee;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.growerhub.backend.common.config.zigbee.ZigbeeWateringSettings;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.zigbee.contract.ZigbeeCommandGateway;
import ru.growerhub.backend.zigbee.contract.ZigbeeWateringData;
import ru.growerhub.backend.zigbee.engine.ZigbeeWateringService;
import ru.growerhub.backend.zigbee.jpa.*;

class ZigbeeWateringServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC);
    private final UUID publicId = UUID.randomUUID();
    private final AuthenticatedUser owner = new AuthenticatedUser(7, "user");
    private final ZigbeeWateringData.Target target = new ZigbeeWateringData.Target(1, "0x123", "state");
    private final ZigbeeCoordinatorRepository coordinators = mock(ZigbeeCoordinatorRepository.class);
    private final ZigbeeDeviceSnapshotRepository devices = mock(ZigbeeDeviceSnapshotRepository.class);
    private final ZigbeeCommandGateway commands = mock(ZigbeeCommandGateway.class);
    private final ZigbeeWateringSettings settings = mock(ZigbeeWateringSettings.class);
    private ZigbeeDeviceSnapshotEntity device;
    private ZigbeeWateringService service;
    private String definitionHash;

    @BeforeEach
    void setup() throws Exception {
        var coordinator = ZigbeeCoordinatorEntity.create(publicId, owner.id(), "Test", "test", "test/valve", LocalDateTime.now(clock));
        when(coordinators.findByIdAndArchivedAtIsNull(1)).thenReturn(Optional.of(coordinator));
        when(coordinators.lockActiveById(1)).thenReturn(Optional.of(coordinator));
        device = ZigbeeDeviceSnapshotEntity.create(1, "Test valve", LocalDateTime.now(clock));
        device.setIeeeAddress(target.ieeeAddress());
        device.setAvailability("online");
        device.setLastLiveStateAt(LocalDateTime.now(clock));
        device.setLiveStateJson("{\"state\":\"OFF\"}");
        device.setBridgeDeviceJson("""
                {"software_build_id":"fixture-v1","definition":{"model":"Test only","exposes":[
                  {"type":"binary","property":"state","access":3,"value_on":"ON","value_off":"OFF"},
                  {"type":"numeric","property":"duration","access":2,"value_min":1,"value_max":600,"value_step":1}
                ]}}
                """);
        definitionHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                mapper.readTree(device.getBridgeDeviceJson()).path("definition").toString().getBytes(StandardCharsets.UTF_8)));
        when(devices.findByCoordinatorIdAndIeeeAddress(1, target.ieeeAddress())).thenReturn(Optional.of(device));
        when(devices.lockWateringDevice(1, target.ieeeAddress())).thenReturn(Optional.of(device));
        when(settings.enabled()).thenReturn(true);
        when(settings.stateFreshnessSeconds()).thenReturn(120);
        when(settings.verifiedDevices()).thenReturn(List.of());
        service = new ZigbeeWateringService(coordinators, devices, commands, settings, mapper, clock);
    }

    @Test
    void exposesWithoutVerifiedPhysicalEvidenceNeverPermitWatering() {
        assertThat(service.capabilities(1, target.ieeeAddress()).getFirst().ready()).isFalse();
        assertThatThrownBy(() -> service.start(target, 30, owner)).isInstanceOf(DomainException.class);
        verifyNoInteractions(commands);
    }

    @Test
    void verifiedFixtureUsesOneBoundedCommandAndKeepsStopAfterFlagRevocation() {
        approve(publicId, "fixture-v1", definitionHash, true);
        service.start(target, 30, owner);
        verify(commands).publishWateringStart("test/valve", "Test valve", Map.of("state", "ON", "duration", 30));
        when(settings.enabled()).thenReturn(false);
        assertThatThrownBy(() -> service.start(target, 30, owner)).isInstanceOf(DomainException.class);
        service.stop(target, "OFF", owner);
        verify(commands).publishSet("test/valve", "Test valve", Map.of("state", "OFF"));
    }

    @Test
    void firmwareDefinitionCoordinatorAndDuplicatePolicyArePartOfApproval() {
        approve(publicId, "old-firmware", definitionHash, true);
        assertThat(service.resolve(target, owner, false).capability().ready()).isFalse();
        approve(publicId, "fixture-v1", "wrong-hash", true);
        assertThat(service.resolve(target, owner, false).capability().ready()).isFalse();
        approve(UUID.randomUUID(), "fixture-v1", definitionHash, true);
        assertThat(service.resolve(target, owner, false).capability().ready()).isFalse();
        approve(publicId, "fixture-v1", definitionHash, false);
        assertThat(service.resolve(target, owner, false).capability().ready()).isFalse();
        verifyNoInteractions(commands);
    }

    @Test
    void rejectsOtherOwnerStaleStateUnknownStateAndExcessiveDuration() {
        approve(publicId, "fixture-v1", definitionHash, true);
        assertThatThrownBy(() -> service.start(target, 30, new AuthenticatedUser(8, "user"))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.start(target, 601, owner)).isInstanceOf(DomainException.class);
        device.setLiveStateJson("{}");
        assertThatThrownBy(() -> service.start(target, 30, owner)).isInstanceOf(DomainException.class);
        device.setLiveStateJson("{\"state\":\"OFF\"}");
        device.setLastLiveStateAt(LocalDateTime.now(clock).minusSeconds(121));
        assertThatThrownBy(() -> service.start(target, 30, owner)).isInstanceOf(DomainException.class);
        verifyNoInteractions(commands);
    }

    private void approve(UUID coordinator, String firmware, String hash, boolean duplicateSafe) {
        // Profil tolko dlya testa: ne yavlyaetsya dopuskom real'noj modeli.
        when(settings.verifiedDevices()).thenReturn(List.of(new ZigbeeWateringSettings.VerifiedDevice(
                coordinator, target.ieeeAddress(), hash, "state", "duration", "ON", "OFF", 600, 1,
                "2026-09-24T12:00:00Z", "test-fixture-only", firmware, duplicateSafe)));
    }

    @Test
    void genericControlsCannotOpenOrExtendKnownWateringValve() {
        approve(publicId, "fixture-v1", definitionHash, true);
        assertThatThrownBy(() -> service.requireGenericCommandAllowed(1, device, "state", "ON")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.requireGenericCommandAllowed(1, device, "duration", 60)).isInstanceOf(DomainException.class);
        service.requireGenericCommandAllowed(1, device, "state", "OFF");
        verifyNoInteractions(commands);
    }
}
