package com.portfolio.fincore.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "bank_accounts")
public class BankAccount {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private DemoUser owner;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Column(name = "masked_account_number", nullable = false, length = 24)
    private String maskedAccountNumber;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    protected BankAccount() {
    }

    public BankAccount(
            String id,
            DemoUser owner,
            String displayName,
            AccountType accountType,
            String maskedAccountNumber,
            String currency,
            long balanceMinor) {
        this.id = id;
        this.owner = owner;
        this.displayName = displayName;
        this.accountType = accountType;
        this.maskedAccountNumber = maskedAccountNumber;
        this.currency = currency;
        this.balanceMinor = balanceMinor;
        this.status = AccountStatus.ACTIVE;
    }

    public String getId() {
        return id;
    }

    public DemoUser getOwner() {
        return owner;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public String getMaskedAccountNumber() {
        return maskedAccountNumber;
    }

    public String getCurrency() {
        return currency;
    }

    public long getBalanceMinor() {
        return balanceMinor;
    }

    public void setBalanceMinor(long balanceMinor) {
        this.balanceMinor = balanceMinor;
    }

    public AccountStatus getStatus() {
        return status;
    }
}