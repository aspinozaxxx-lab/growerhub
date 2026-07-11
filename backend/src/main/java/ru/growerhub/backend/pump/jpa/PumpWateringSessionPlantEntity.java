package ru.growerhub.backend.pump.jpa;

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
        name = "pump_watering_session_plants",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_pump_watering_session_plants_target",
                columnNames = {"session_box_id", "plant_id"}
        )
)
public class PumpWateringSessionPlantEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PumpWateringSessionEntity session;

    @ManyToOne
    @JoinColumn(name = "session_box_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PumpWateringSessionBoxEntity sessionBox;

    @Column(name = "plant_id", nullable = false)
    private Integer plantId;

    @Column(name = "plant_name")
    private String plantName;

    @Column(name = "owner_id")
    private Integer ownerId;

    @Column(name = "rate_ml_per_hour")
    private Integer rateMlPerHour;

    @Column(name = "duration_s")
    private Integer durationS;

    @Column(name = "water_volume_l")
    private Double waterVolumeL;

    protected PumpWateringSessionPlantEntity() {
    }

    public static PumpWateringSessionPlantEntity create() { return new PumpWateringSessionPlantEntity(); }
    public Long getId() { return id; }
    public PumpWateringSessionEntity getSession() { return session; }
    public void setSession(PumpWateringSessionEntity session) { this.session = session; }
    public PumpWateringSessionBoxEntity getSessionBox() { return sessionBox; }
    public void setSessionBox(PumpWateringSessionBoxEntity sessionBox) { this.sessionBox = sessionBox; }
    public Integer getPlantId() { return plantId; }
    public void setPlantId(Integer plantId) { this.plantId = plantId; }
    public String getPlantName() { return plantName; }
    public void setPlantName(String plantName) { this.plantName = plantName; }
    public Integer getOwnerId() { return ownerId; }
    public void setOwnerId(Integer ownerId) { this.ownerId = ownerId; }
    public Integer getRateMlPerHour() { return rateMlPerHour; }
    public void setRateMlPerHour(Integer rateMlPerHour) { this.rateMlPerHour = rateMlPerHour; }
    public Integer getDurationS() { return durationS; }
    public void setDurationS(Integer durationS) { this.durationS = durationS; }
    public Double getWaterVolumeL() { return waterVolumeL; }
    public void setWaterVolumeL(Double waterVolumeL) { this.waterVolumeL = waterVolumeL; }
}
