CREATE TABLE payment_escrow (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipment_record(id),
    business_id UUID NOT NULL REFERENCES business_profile(id),
    supplier_profile_id UUID NOT NULL REFERENCES supplier_profile(id),
    protected_supplier_phone TEXT NOT NULL,
    currency CHAR(3) NOT NULL,
    agreed_amount NUMERIC(19, 4) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT payment_escrow_shipment_business_unique UNIQUE (shipment_id, business_id),
    CONSTRAINT payment_escrow_currency_shape CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT payment_escrow_amount_positive CHECK (agreed_amount > 0),
    CONSTRAINT payment_escrow_status_known CHECK (status IN (
        'LOCK_REQUESTED', 'LOCK_PENDING', 'LOCKED', 'LOCK_FAILED',
        'RELEASE_REQUESTED', 'RELEASE_PENDING', 'RELEASED', 'RELEASE_FAILED'
    )),
    CONSTRAINT payment_escrow_time_order CHECK (updated_at >= created_at)
);

CREATE INDEX payment_escrow_business_status_idx
    ON payment_escrow(business_id, status, updated_at DESC);

CREATE TABLE payment_escrow_transaction (
    id UUID PRIMARY KEY,
    escrow_id UUID NOT NULL REFERENCES payment_escrow(id) ON DELETE CASCADE,
    command_id UUID NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    sequence INTEGER NOT NULL,
    provider_reference UUID NOT NULL UNIQUE,
    protected_phone TEXT NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    status VARCHAR(16) NOT NULL,
    deadline_at TIMESTAMPTZ NOT NULL,
    failure_code VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT payment_escrow_transaction_command_unique
        UNIQUE (escrow_id, transaction_type, command_id),
    CONSTRAINT payment_escrow_transaction_sequence_unique
        UNIQUE (escrow_id, transaction_type, sequence),
    CONSTRAINT payment_escrow_transaction_type_known CHECK (
        transaction_type IN ('LOCK', 'RELEASE')
    ),
    CONSTRAINT payment_escrow_transaction_status_known CHECK (
        status IN ('REQUESTED', 'PENDING', 'SUCCESSFUL', 'FAILED', 'TIMED_OUT')
    ),
    CONSTRAINT payment_escrow_transaction_values_valid CHECK (
        sequence >= 0 AND amount > 0 AND deadline_at > created_at AND updated_at >= created_at
    ),
    CONSTRAINT payment_escrow_transaction_failure_consistent CHECK (
        (status IN ('FAILED', 'TIMED_OUT') AND failure_code IS NOT NULL)
        OR (status IN ('REQUESTED', 'PENDING', 'SUCCESSFUL') AND failure_code IS NULL)
    )
);

CREATE INDEX payment_escrow_transaction_status_idx
    ON payment_escrow_transaction(status, deadline_at, updated_at);
