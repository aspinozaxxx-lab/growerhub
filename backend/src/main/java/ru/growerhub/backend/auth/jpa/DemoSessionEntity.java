package ru.growerhub.backend.auth.jpa;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "auth_demo_sessions")
public class DemoSessionEntity {
    @Id public UUID id;
    @Column(name = "space_id", nullable = false) public UUID spaceId;
    @Column(nullable = false) public int generation;
    @Column(name = "account_user_id") public Integer accountUserId;
    @Column(name = "refresh_hash", nullable = false, unique = true) public String refreshHash;
    @Column(name = "expires_at", nullable = false) public LocalDateTime expiresAt;
}
