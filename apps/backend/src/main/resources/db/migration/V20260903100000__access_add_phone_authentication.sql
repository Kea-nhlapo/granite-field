ALTER TABLE access_user_account
    ALTER COLUMN email DROP NOT NULL,
    ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE access_user_account
    ADD CONSTRAINT access_user_account_credentials_pair CHECK (
        (email IS NULL AND password_hash IS NULL)
        OR (email IS NOT NULL AND password_hash IS NOT NULL)
    );

CREATE TABLE access_phone_identity (
    phone_number VARCHAR(16) PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES access_user_account(id) ON DELETE CASCADE,
    verification_method VARCHAR(32) NOT NULL,
    verified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT access_phone_identity_e164 CHECK (
        phone_number ~ '^[+][1-9][0-9]{7,14}$'
    ),
    CONSTRAINT access_phone_identity_method CHECK (
        verification_method IN ('TWILIO_OTP', 'MOMO_CONSENT')
    )
);

CREATE TABLE access_otp_send_limit (
    phone_hash CHAR(64) PRIMARY KEY,
    last_sent_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE access_momo_sign_in (
    id UUID PRIMARY KEY,
    poll_token_hash CHAR(64) NOT NULL UNIQUE,
    phone_number VARCHAR(16) NOT NULL,
    provider_reference VARCHAR(128) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT access_momo_sign_in_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')
    )
);

CREATE INDEX access_momo_sign_in_active_idx
    ON access_momo_sign_in(poll_token_hash, expires_at)
    WHERE completed_at IS NULL;

CREATE TABLE access_momo_profile (
    user_id UUID PRIMARY KEY REFERENCES access_user_account(id) ON DELETE CASCADE,
    phone_number VARCHAR(16) NOT NULL,
    given_name VARCHAR(120),
    family_name VARCHAR(120),
    locale VARCHAR(32),
    verified_at TIMESTAMPTZ NOT NULL
);
