CREATE TABLE demo_users (
    id VARCHAR(80) PRIMARY KEY,
    demo_key VARCHAR(40) NOT NULL UNIQUE,
    display_name VARCHAR(80) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER', 'OPERATIONS', 'AUDITOR')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE bank_accounts (
    id VARCHAR(80) PRIMARY KEY,
    owner_user_id VARCHAR(80) NOT NULL REFERENCES demo_users(id),
    display_name VARCHAR(80) NOT NULL,
    account_type VARCHAR(20) NOT NULL CHECK (account_type IN ('CHECKING', 'SAVINGS')),
    masked_account_number VARCHAR(24) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency = 'USD'),
    balance_minor BIGINT NOT NULL CHECK (balance_minor >= 0),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN')),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX bank_accounts_owner_idx ON bank_accounts(owner_user_id, display_name);

CREATE TABLE beneficiaries (
    id VARCHAR(80) PRIMARY KEY,
    owner_user_id VARCHAR(80) NOT NULL REFERENCES demo_users(id),
    destination_account_id VARCHAR(80) NOT NULL REFERENCES bank_accounts(id),
    alias VARCHAR(80) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT beneficiary_target_unique UNIQUE (owner_user_id, destination_account_id)
);

CREATE TABLE transfers (
    id VARCHAR(80) PRIMARY KEY,
    owner_user_id VARCHAR(80) NOT NULL REFERENCES demo_users(id),
    source_account_id VARCHAR(80) NOT NULL REFERENCES bank_accounts(id),
    destination_account_id VARCHAR(80) NOT NULL REFERENCES bank_accounts(id),
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK (currency = 'USD'),
    memo VARCHAR(120) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('COMPLETED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT transfer_not_same_account CHECK (source_account_id <> destination_account_id),
    CONSTRAINT transfer_idempotency_unique UNIQUE (owner_user_id, idempotency_key)
);

CREATE INDEX transfers_owner_created_idx ON transfers(owner_user_id, created_at DESC);
CREATE INDEX transfers_source_idx ON transfers(source_account_id, created_at DESC);
CREATE INDEX transfers_destination_idx ON transfers(destination_account_id, created_at DESC);

CREATE TABLE ledger_entries (
    id VARCHAR(80) PRIMARY KEY,
    account_id VARCHAR(80) NOT NULL REFERENCES bank_accounts(id),
    transfer_id VARCHAR(80) REFERENCES transfers(id),
    amount_minor BIGINT NOT NULL CHECK (amount_minor <> 0),
    balance_after_minor BIGINT NOT NULL CHECK (balance_after_minor >= 0),
    category VARCHAR(24) NOT NULL CHECK (category IN (
        'INCOME', 'HOUSING', 'GROCERIES', 'TRANSPORT', 'UTILITIES', 'DINING', 'TRANSFER', 'OTHER'
    )),
    description VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ledger_account_created_idx ON ledger_entries(account_id, created_at DESC);
CREATE INDEX ledger_transfer_idx ON ledger_entries(transfer_id);

CREATE TABLE audit_events (
    id VARCHAR(80) PRIMARY KEY,
    actor_user_id VARCHAR(80) NOT NULL REFERENCES demo_users(id),
    event_name VARCHAR(80) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id VARCHAR(80) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX audit_actor_created_idx ON audit_events(actor_user_id, created_at DESC);