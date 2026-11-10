package com.portfolio.fincore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "beneficiaries", uniqueConstraints = @UniqueConstraint(
        columnNames = {"owner_user_id", "destination_account_id"}))
public class Beneficiary {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private DemoUser owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_account_id", nullable = false)
    private BankAccount destinationAccount;

    @Column(nullable = false, length = 80)
    private String alias;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Beneficiary() {
    }

    public Beneficiary(String id, DemoUser owner, BankAccount destinationAccount, String alias) {
        this.id = id;
        this.owner = owner;
        this.destinationAccount = destinationAccount;
        this.alias = alias;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public DemoUser getOwner() {
        return owner;
    }

    public BankAccount getDestinationAccount() {
        return destinationAccount;
    }

    public String getAlias() {
        return alias;
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        active = false;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}