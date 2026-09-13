package ru.growerhub.backend.demo.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "demo_capacity")
public class DemoCapacityEntity {
    @Id public Integer id;
}
