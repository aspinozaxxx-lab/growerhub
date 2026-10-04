package ru.growerhub.backend.journal.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareReminderRepository extends JpaRepository<CareReminderEntity, Integer> {
    List<CareReminderEntity> findByUserIdAndEnabledTrueOrderByDueAtAsc(Integer userId);
    long countByUserIdAndEnabledTrue(Integer userId);
    void deleteAllByPlantId(Integer plantId);
}
