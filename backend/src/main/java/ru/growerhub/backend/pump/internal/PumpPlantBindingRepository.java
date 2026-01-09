package ru.growerhub.backend.pump.internal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.growerhub.backend.pump.PumpPlantBindingEntity;

public interface PumpPlantBindingRepository extends JpaRepository<PumpPlantBindingEntity, Integer> {
    List<PumpPlantBindingEntity> findAllByPump_Id(Integer pumpId);

    List<PumpPlantBindingEntity> findAllByPump_IdIn(List<Integer> pumpIds);

    List<PumpPlantBindingEntity> findAllByPlant_Id(Integer plantId);

    void deleteAllByPump_Id(Integer pumpId);
}
