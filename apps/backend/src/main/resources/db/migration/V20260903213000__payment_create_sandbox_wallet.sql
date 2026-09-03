CREATE TABLE payment_sandbox_wallet (
    user_id UUID PRIMARY KEY REFERENCES access_user_account(id) ON DELETE CASCADE,
    display_name VARCHAR(160) NOT NULL,
    currency CHAR(3) NOT NULL,
    available_balance NUMERIC(19, 4) NOT NULL,
    held_balance NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT payment_sandbox_wallet_currency CHECK (currency = 'ZAR')
);

CREATE TABLE payment_sandbox_wallet_entry (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES payment_sandbox_wallet(user_id) ON DELETE CASCADE,
    reference_key VARCHAR(180) NOT NULL UNIQUE,
    entry_type VARCHAR(40) NOT NULL,
    available_delta NUMERIC(19, 4) NOT NULL,
    held_delta NUMERIC(19, 4) NOT NULL,
    available_balance_after NUMERIC(19, 4) NOT NULL,
    held_balance_after NUMERIC(19, 4) NOT NULL,
    description VARCHAR(240) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT payment_sandbox_wallet_entry_type CHECK (entry_type IN (
        'OPENING_CREDIT',
        'ESCROW_HELD',
        'ESCROW_SETTLED',
        'PAYMENT_RECEIVED'
    ))
);

CREATE INDEX payment_sandbox_wallet_entry_user_idx
    ON payment_sandbox_wallet_entry(user_id, created_at DESC, id DESC);
