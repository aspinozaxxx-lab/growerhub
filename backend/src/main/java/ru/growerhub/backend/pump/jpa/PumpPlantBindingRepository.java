package ru.growerhub.backend.pump.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PumpPlantBindingRepository extends JpaRepository<PumpPlantBindingEntity, Integer> {
    List<PumpPlantBindingEntity> findAllByPump_Id(Integer pumpId);

    List<PumpPlantBindingEntity> findAllByPump_IdIn(List<Integer> pumpIds);

    List<PumpPlantBindingEntity> findAllByPlantId(Integer plantId);

    void deleteAllByPump_Id(Integer pumpId);
}

