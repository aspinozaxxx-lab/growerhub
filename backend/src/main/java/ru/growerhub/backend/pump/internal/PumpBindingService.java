package ru.growerhub.backend.pump.internal;

import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.common.contract.AuthenticatedUser;
import ru.growerhub.backend.common.contract.DomainException;
import ru.growerhub.backend.device.DeviceAccessService;
import ru.growerhub.backend.device.contract.DeviceSummary;
import ru.growerhub.backend.plant.jpa.PlantEntity;
import ru.growerhub.backend.pump.PumpEntity;
import ru.growerhub.backend.pump.PumpPlantBindingEntity;

@Service
public class PumpBindingService {
    private final PumpRepository pumpRepository;
    private final PumpPlantBindingRepository bindingRepository;
    private final EntityManager entityManager;
    private final DeviceAccessService deviceAccessService;

    public PumpBindingService(
            PumpRepository pumpRepository,
            PumpPlantBindingRepository bindingRepository,
            EntityManager entityManager,
            DeviceAccessService deviceAccessService
    ) {
        this.pumpRepository = pumpRepository;
        this.bindingRepository = bindingRepository;
        this.entityManager = entityManager;
        this.deviceAccessService = deviceAccessService;
    }

    @Transactional
    public void updateBindings(Integer pumpId, List<PumpBindingItem> items, AuthenticatedUser user) {
        PumpEntity pump = pumpRepository.findById(pumpId).orElse(null);
        if (pump == null) {
            throw new DomainException("not_found", "nasos ne naiden");
        }
        if (!isAdmin(user)) {
            DeviceSummary deviceSummary = deviceAccessService.getDeviceSummary(pump.getDeviceId());
            Integer ownerId = deviceSummary != null ? deviceSummary.userId() : null;
            if (ownerId == null || !ownerId.equals(user.id())) {
                throw new DomainException("forbidden", "nedostatochno prav dlya etogo nasosa");
            }
        }

        Map<Integer, Integer> nextRates = new HashMap<>();
        if (items != null) {
            for (PumpBindingItem item : items) {
                if (item == null || item.plantId() == null) {
                    continue;
                }
                int rate = item.rateMlPerHour() != null ? item.rateMlPerHour() : 2000;
                if (rate <= 0) {
                    throw new DomainException("unprocessable", "rate_ml_per_hour dolzhen byt' > 0");
                }
                nextRates.put(item.plantId(), rate);
            }
        }

        List<PumpPlantBindingEntity> current = bindingRepository.findAllByPump_Id(pumpId);
        Set<Integer> currentIds = current.stream()
                .map(binding -> binding.getPlant().getId())
                .collect(Collectors.toSet());

        for (PumpPlantBindingEntity binding : current) {
            Integer plantId = binding.getPlant() != null ? binding.getPlant().getId() : null;
            if (plantId == null || !nextRates.containsKey(plantId)) {
                bindingRepository.delete(binding);
            }
        }

        for (Map.Entry<Integer, Integer> entry : nextRates.entrySet()) {
            Integer plantId = entry.getKey();
            Integer rate = entry.getValue();
            PlantEntity plant = resolvePlant(plantId, user);
            if (currentIds.contains(plantId)) {
                for (PumpPlantBindingEntity binding : current) {
                    if (binding.getPlant() != null && plantId.equals(binding.getPlant().getId())) {
                        binding.setRateMlPerHour(rate);
                        bindingRepository.save(binding);
                    }
                }
                continue;
            }
            PumpPlantBindingEntity binding = PumpPlantBindingEntity.create();
            binding.setPump(pump);
            binding.setPlant(plant);
            binding.setRateMlPerHour(rate);
            bindingRepository.save(binding);
        }
    }

    private PlantEntity resolvePlant(Integer plantId, AuthenticatedUser user) {
        PlantEntity plant = entityManager.find(PlantEntity.class, plantId);
        if (plant == null) {
            throw new DomainException("not_found", "rastenie ne naideno");
        }
        if (!isAdmin(user)) {
            if (plant.getUser() == null || !plant.getUser().getId().equals(user.id())) {
                throw new DomainException("forbidden", "rastenie ne naideno");
            }
        }
        return plant;
    }

    private boolean isAdmin(AuthenticatedUser user) {
        return user != null && user.isAdmin();
    }

    public record PumpBindingItem(Integer plantId, Integer rateMlPerHour) {
    }
}

