package com.portfolio.fincore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private DemoUser actor;

    @Column(name = "event_name", nullable = false, length = 80)
    private String eventName;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 80)
    private String entityId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEvent() {
    }

    public AuditEvent(String id, DemoUser actor, String eventName, String entityType, String entityId) {
        this.id = id;
        this.actor = actor;
        this.eventName = eventName;
        this.entityType = entityType;
        this.entityId = entityId;
        this.createdAt = Instant.now();
    }
}