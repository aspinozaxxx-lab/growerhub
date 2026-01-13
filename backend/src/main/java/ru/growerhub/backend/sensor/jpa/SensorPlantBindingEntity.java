package ru.growerhub.backend.sensor.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(
    name = "sensor_plant_bindings",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_sensor_plant_bindings_pair", columnNames = {"sensor_id", "plant_id"})
    }
)
public class SensorPlantBindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "sensor_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SensorEntity sensor;

    @Column(name = "plant_id", nullable = false)
    private Integer plantId;

    protected SensorPlantBindingEntity() {
    }

    public static SensorPlantBindingEntity create() {
        return new SensorPlantBindingEntity();
    }

    public Integer getId() {
        return id;
    }

    public SensorEntity getSensor() {
        return sensor;
    }

    public void setSensor(SensorEntity sensor) {
        this.sensor = sensor;
    }

    public Integer getPlantId() {
        return plantId;
    }

    public void setPlantId(Integer plantId) {
        this.plantId = plantId;
    }
}


