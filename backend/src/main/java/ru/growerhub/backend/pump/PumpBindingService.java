package ru.growerhub.backend.pump;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.growerhub.backend.api.ApiException;
import ru.growerhub.backend.plant.PlantEntity;
import ru.growerhub.backend.plant.PlantRepository;
import ru.growerhub.backend.user.UserEntity;

@Service
public class PumpBindingService {
    private final PumpRepository pumpRepository;
    private final PumpPlantBindingRepository bindingRepository;
    private final PlantRepository plantRepository;

    public PumpBindingService(
            PumpRepository pumpRepository,
            PumpPlantBindingRepository bindingRepository,
            PlantRepository plantRepository
    ) {
        this.pumpRepository = pumpRepository;
        this.bindingRepository = bindingRepository;
        this.plantRepository = plantRepository;
    }

    @Transactional
    public void updateBindings(Integer pumpId, List<PumpBindingItem> items, UserEntity user) {
        PumpEntity pump = pumpRepository.findById(pumpId).orElse(null);
        if (pump == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "nasos ne naiden");
        }
        if (!isAdmin(user)) {
            if (pump.getDevice() == null || pump.getDevice().getUser() == null) {
                throw new ApiException(HttpStatus.FORBIDDEN, "nedostatochno prav dlya etogo nasosa");
            }
            if (!pump.getDevice().getUser().getId().equals(user.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "nedostatochno prav dlya etogo nasosa");
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
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "rate_ml_per_hour dolzhen byt' > 0");
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

    private PlantEntity resolvePlant(Integer plantId, UserEntity user) {
        PlantEntity plant;
        if (isAdmin(user)) {
            plant = plantRepository.findById(plantId).orElse(null);
        } else {
            plant = plantRepository.findByIdAndUser_Id(plantId, user.getId()).orElse(null);
        }
        if (plant == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "rastenie ne naideno");
        }
        return plant;
    }

    private boolean isAdmin(UserEntity user) {
        return user != null && "admin".equals(user.getRole());
    }

    public record PumpBindingItem(Integer plantId, Integer rateMlPerHour) {
    }
}
