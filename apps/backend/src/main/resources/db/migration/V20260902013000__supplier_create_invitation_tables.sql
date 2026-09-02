CREATE TABLE supplier_profile (
    id UUID PRIMARY KEY,
    normalized_email VARCHAR(320) NOT NULL UNIQUE,
    profile_status VARCHAR(32) NOT NULL,
    claimed_user_id UUID UNIQUE REFERENCES access_user_account(id),
    business_id UUID UNIQUE REFERENCES business_profile(id),
    created_at TIMESTAMPTZ NOT NULL,
    converted_at TIMESTAMPTZ,
    CONSTRAINT supplier_profile_status_known CHECK (
        profile_status IN ('TEMPORARY', 'REGISTERED')
    ),
    CONSTRAINT supplier_profile_conversion_consistent CHECK (
        (profile_status = 'TEMPORARY'
            AND claimed_user_id IS NULL
            AND business_id IS NULL
            AND converted_at IS NULL)
        OR
        (profile_status = 'REGISTERED'
            AND claimed_user_id IS NOT NULL
            AND converted_at IS NOT NULL)
    )
);

CREATE TABLE supplier_invitation (
    id UUID PRIMARY KEY,
    buyer_business_id UUID NOT NULL REFERENCES business_profile(id),
    supplier_profile_id UUID NOT NULL REFERENCES supplier_profile(id),
    request_id UUID NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    response_reference UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT supplier_invitation_purpose_known CHECK (
        purpose IN ('QUOTE_RESPONSE')
    ),
    CONSTRAINT supplier_invitation_status_known CHECK (
        status IN ('PENDING', 'RESPONDED', 'REVOKED', 'EXPIRED')
    ),
    CONSTRAINT supplier_invitation_token_hash_shape CHECK (
        token_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT supplier_invitation_expiry_after_creation CHECK (
        expires_at > created_at
    ),
    CONSTRAINT supplier_invitation_state_consistent CHECK (
        (status = 'PENDING'
            AND response_reference IS NULL
            AND responded_at IS NULL
            AND revoked_at IS NULL)
        OR
        (status = 'RESPONDED'
            AND response_reference IS NOT NULL
            AND responded_at IS NOT NULL
            AND revoked_at IS NULL)
        OR
        (status = 'REVOKED'
            AND response_reference IS NULL
            AND responded_at IS NULL
            AND revoked_at IS NOT NULL)
        OR
        (status = 'EXPIRED'
            AND response_reference IS NULL
            AND responded_at IS NULL
            AND revoked_at IS NULL)
    )
);

CREATE UNIQUE INDEX supplier_invitation_one_pending_scope_idx
    ON supplier_invitation(buyer_business_id, request_id, supplier_profile_id)
    WHERE status = 'PENDING';

CREATE INDEX supplier_invitation_buyer_idx
    ON supplier_invitation(buyer_business_id, created_at DESC);

CREATE INDEX supplier_invitation_profile_idx
    ON supplier_invitation(supplier_profile_id, created_at DESC);
