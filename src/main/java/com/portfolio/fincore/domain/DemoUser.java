package com.portfolio.fincore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "demo_users", uniqueConstraints = @UniqueConstraint(columnNames = "demo_key"))
public class DemoUser {
    @Id
    private String id;

    @Column(name = "demo_key", nullable = false, length = 40)
    private String demoKey;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DemoRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DemoUser() {
    }

    public DemoUser(String id, String demoKey, String displayName, DemoRole role) {
        this.id = id;
        this.demoKey = demoKey;
        this.displayName = displayName;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getDemoKey() {
        return demoKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public DemoRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}