package ru.growerhub.backend.pump.engine;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.pump.jpa.PumpEntity;
import ru.growerhub.backend.pump.jpa.PumpRepository;

@Service
public class PumpService {
    private static final int DEFAULT_CHANNEL = 0;

    private final PumpRepository pumpRepository;

    public PumpService(PumpRepository pumpRepository) {
        this.pumpRepository = pumpRepository;
    }

    @Transactional
    public PumpEntity ensureDefaultPump(Integer deviceId) {
        return ensurePump(deviceId, DEFAULT_CHANNEL);
    }

    @Transactional
    public PumpEntity ensurePump(Integer deviceId, int channel) {
        if (deviceId == null) {
            return null;
        }
        PumpEntity existing = pumpRepository.findByDeviceIdAndChannel(deviceId, channel).orElse(null);
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        PumpEntity pump = PumpEntity.create();
        pump.setDeviceId(deviceId);
        pump.setChannel(channel);
        pump.setCreatedAt(now);
        pump.setUpdatedAt(now);
        return pumpRepository.save(pump);
    }

    @Transactional
    public List<PumpEntity> listByDevice(Integer deviceId, boolean ensureDefault) {
        if (deviceId == null) {
            return List.of();
        }
        List<PumpEntity> pumps = pumpRepository.findAllByDeviceId(deviceId);
        if (pumps.isEmpty() && ensureDefault) {
            PumpEntity created = ensureDefaultPump(deviceId);
            if (created != null) {
                return List.of(created);
            }
        }
        return pumps;
    }

    @Transactional
    public void deleteAllByDeviceId(Integer deviceId) {
        if (deviceId == null) {
            return;
        }
        pumpRepository.deleteAllByDeviceId(deviceId);
    }
}


