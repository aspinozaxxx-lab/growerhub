package ru.growerhub.backend.advisor.jpa;
import jakarta.persistence.*;
@Entity @Table(name = "assistant_guard")
public class AssistantGuardEntity {
    @Id public Integer id;
    public boolean publicEnabled;
    public long rejected;
    public String alertKey;
    @Column(columnDefinition = "TEXT") public String denialsJson = "{}";
}
