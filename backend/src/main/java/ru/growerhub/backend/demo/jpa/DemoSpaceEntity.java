package ru.growerhub.backend.demo.jpa;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "demo_spaces")
public class DemoSpaceEntity {
    @Id public UUID id;
    @Column(name = "data_user_id", nullable = false, unique = true) public Integer dataUserId;
    @Column(name = "account_user_id", unique = true) public Integer accountUserId;
    @Column(nullable = false) public int generation;
    @Column(name = "template_version", nullable = false) public int templateVersion;
    @Column(nullable = false) public String locale;
    @Column(name = "admission_key", nullable = false) public String admissionKey;
    @Column(name = "created_at", nullable = false) public LocalDateTime createdAt;
    @Column(name = "last_active_at", nullable = false) public LocalDateTime lastActiveAt;
    @Column(name = "last_tick_at") public LocalDateTime lastTickAt;
    @Column(name = "action_window_started_at") public LocalDateTime actionWindowStartedAt;
    @Column(name = "actions_in_window", nullable = false) public int actionsInWindow;
    @Column(name = "reset_window_started_at") public LocalDateTime resetWindowStartedAt;
    @Column(name = "resets_in_window", nullable = false) public int resetsInWindow;
    @Column(name = "expires_at") public LocalDateTime expiresAt;
    @Column(nullable = false) public boolean paused;
}
