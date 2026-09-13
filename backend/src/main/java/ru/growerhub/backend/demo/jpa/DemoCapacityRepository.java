package ru.growerhub.backend.demo.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DemoCapacityRepository extends JpaRepository<DemoCapacityEntity, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DemoCapacityEntity c where c.id = 1")
    DemoCapacityEntity lockCapacity();
}
