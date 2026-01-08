package ru.growerhub.backend.pump;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.device.DeviceEntity;

@Service
public class PumpService {
    private static final int DEFAULT_CHANNEL = 0;

    private final PumpRepository pumpRepository;

    public PumpService(PumpRepository pumpRepository) {
        this.pumpRepository = pumpRepository;
    }

    @Transactional
    public PumpEntity ensureDefaultPump(DeviceEntity device) {
        return ensurePump(device, DEFAULT_CHANNEL);
    }

    @Transactional
    public PumpEntity ensurePump(DeviceEntity device, int channel) {
        if (device == null || device.getId() == null) {
            return null;
        }
        PumpEntity existing = pumpRepository.findByDevice_IdAndChannel(device.getId(), channel).orElse(null);
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        PumpEntity pump = PumpEntity.create();
        pump.setDevice(device);
        pump.setChannel(channel);
        pump.setCreatedAt(now);
        pump.setUpdatedAt(now);
        return pumpRepository.save(pump);
    }

    @Transactional
    public List<PumpEntity> listByDevice(DeviceEntity device, boolean ensureDefault) {
        if (device == null || device.getId() == null) {
            return List.of();
        }
        List<PumpEntity> pumps = pumpRepository.findAllByDevice_Id(device.getId());
        if (pumps.isEmpty() && ensureDefault) {
            PumpEntity created = ensureDefaultPump(device);
            if (created != null) {
                return List.of(created);
            }
        }
        return pumps;
    }
}
