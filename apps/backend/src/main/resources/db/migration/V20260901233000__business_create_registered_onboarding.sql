CREATE TABLE business_profile (
    id UUID PRIMARY KEY,
    registration_number VARCHAR(16) NOT NULL UNIQUE,
    legal_name VARCHAR(255) NOT NULL,
    trading_name VARCHAR(255),
    registered_address TEXT NOT NULL,
    verification_status VARCHAR(32) NOT NULL,
    lifecycle_status VARCHAR(32) NOT NULL,
    confirmed_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT business_profile_verification_status_known CHECK (
        verification_status IN ('REGISTRY_VERIFIED')
    ),
    CONSTRAINT business_profile_lifecycle_status_known CHECK (
        lifecycle_status IN ('ACTIVE')
    )
);

CREATE TABLE business_registered_onboarding (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES access_user_account(id),
    registration_number VARCHAR(16) NOT NULL UNIQUE,
    legal_name VARCHAR(255) NOT NULL,
    trading_name VARCHAR(255),
    registered_address TEXT NOT NULL,
    registry_reference VARCHAR(128) NOT NULL,
    state VARCHAR(32) NOT NULL,
    business_id UUID UNIQUE REFERENCES business_profile(id),
    created_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ,
    CONSTRAINT business_registered_onboarding_state_known CHECK (
        state IN ('PENDING_CONFIRMATION', 'CONFIRMED')
    ),
    CONSTRAINT business_registered_onboarding_confirmation_consistent CHECK (
        (state = 'PENDING_CONFIRMATION' AND business_id IS NULL AND confirmed_at IS NULL)
        OR (state = 'CONFIRMED' AND business_id IS NOT NULL AND confirmed_at IS NOT NULL)
    )
);

CREATE INDEX business_registered_onboarding_owner_idx
    ON business_registered_onboarding(owner_user_id, created_at DESC);

ALTER TABLE access_business_membership
    ADD COLUMN membership_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD CONSTRAINT access_business_membership_known_status CHECK (
        membership_status IN ('ACTIVE', 'SUSPENDED')
    ),
    ADD CONSTRAINT access_business_membership_business_fk
        FOREIGN KEY (business_id) REFERENCES business_profile(id) ON DELETE CASCADE;
