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
        name = "pump_watering_session_boxes",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_pump_watering_session_boxes_session_box",
                columnNames = {"session_id", "box_id"}
        )
)
public class PumpWateringSessionBoxEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PumpWateringSessionEntity session;

    @Column(name = "box_id")
    private Integer boxId;

    @Column(name = "box_name")
    private String boxName;

    @Column(name = "room_id")
    private Integer roomId;

    @Column(name = "room_name")
    private String roomName;

    protected PumpWateringSessionBoxEntity() {
    }

    public static PumpWateringSessionBoxEntity create() { return new PumpWateringSessionBoxEntity(); }
    public Long getId() { return id; }
    public PumpWateringSessionEntity getSession() { return session; }
    public void setSession(PumpWateringSessionEntity session) { this.session = session; }
    public Integer getBoxId() { return boxId; }
    public void setBoxId(Integer boxId) { this.boxId = boxId; }
    public String getBoxName() { return boxName; }
    public void setBoxName(String boxName) { this.boxName = boxName; }
    public Integer getRoomId() { return roomId; }
    public void setRoomId(Integer roomId) { this.roomId = roomId; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
}
