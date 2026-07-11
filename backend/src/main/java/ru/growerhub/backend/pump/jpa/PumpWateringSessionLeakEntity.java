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
        name = "pump_watering_session_leaks",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_pump_watering_session_leaks_target",
                columnNames = {"session_box_id", "reference"}
        )
)
public class PumpWateringSessionLeakEntity {
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

    @Column(name = "reference", nullable = false, length = 512)
    private String reference;

    @Column(name = "resource_binding_id")
    private Integer resourceBindingId;

    @Column(name = "source_type")
    private String sourceType;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "property")
    private String property;

    @Column(name = "label")
    private String label;

    @Column(name = "available_at_start")
    private Boolean availableAtStart;

    @Column(name = "triggered_at_start")
    private Boolean triggeredAtStart;

    protected PumpWateringSessionLeakEntity() {
    }

    public static PumpWateringSessionLeakEntity create() { return new PumpWateringSessionLeakEntity(); }
    public Long getId() { return id; }
    public PumpWateringSessionEntity getSession() { return session; }
    public void setSession(PumpWateringSessionEntity session) { this.session = session; }
    public PumpWateringSessionBoxEntity getSessionBox() { return sessionBox; }
    public void setSessionBox(PumpWateringSessionBoxEntity sessionBox) { this.sessionBox = sessionBox; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public Integer getResourceBindingId() { return resourceBindingId; }
    public void setResourceBindingId(Integer resourceBindingId) { this.resourceBindingId = resourceBindingId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getProperty() { return property; }
    public void setProperty(String property) { this.property = property; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Boolean getAvailableAtStart() { return availableAtStart; }
    public void setAvailableAtStart(Boolean availableAtStart) { this.availableAtStart = availableAtStart; }
    public Boolean getTriggeredAtStart() { return triggeredAtStart; }
    public void setTriggeredAtStart(Boolean triggeredAtStart) { this.triggeredAtStart = triggeredAtStart; }
}
