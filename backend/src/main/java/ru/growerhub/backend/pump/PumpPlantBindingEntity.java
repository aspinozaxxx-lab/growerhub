package ru.growerhub.backend.pump;

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
import ru.growerhub.backend.plant.PlantEntity;

@Entity
@Table(
    name = "pump_plant_bindings",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_pump_plant_bindings_pair", columnNames = {"pump_id", "plant_id"})
    }
)
public class PumpPlantBindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "pump_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PumpEntity pump;

    @ManyToOne
    @JoinColumn(name = "plant_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PlantEntity plant;

    @Column(name = "rate_ml_per_hour", nullable = false)
    private Integer rateMlPerHour = 2000;

    protected PumpPlantBindingEntity() {
    }

    public static PumpPlantBindingEntity create() {
        return new PumpPlantBindingEntity();
    }

    public Integer getId() {
        return id;
    }

    public PumpEntity getPump() {
        return pump;
    }

    public void setPump(PumpEntity pump) {
        this.pump = pump;
    }

    public PlantEntity getPlant() {
        return plant;
    }

    public void setPlant(PlantEntity plant) {
        this.plant = plant;
    }

    public Integer getRateMlPerHour() {
        return rateMlPerHour;
    }

    public void setRateMlPerHour(Integer rateMlPerHour) {
        this.rateMlPerHour = rateMlPerHour;
    }
}
