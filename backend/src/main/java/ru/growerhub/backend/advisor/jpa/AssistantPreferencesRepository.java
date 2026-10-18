package ru.growerhub.backend.advisor.jpa;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AssistantPreferencesRepository extends JpaRepository<AssistantPreferencesEntity, Integer> {
    List<AssistantPreferencesEntity> findByReportNot(String report);
}
