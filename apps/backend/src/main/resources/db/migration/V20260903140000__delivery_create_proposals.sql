CREATE TABLE delivery_proposal (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business_profile(id),
    shipment_id UUID NOT NULL REFERENCES shipment_record(id),
    client_request_id UUID NOT NULL,
    input_fingerprint CHAR(64) NOT NULL,
    recipient_email VARCHAR(320) NOT NULL,
    recipient_phone VARCHAR(16) NOT NULL,
    mobile_channel VARCHAR(16) NOT NULL,
    confirmation_token_hash CHAR(64) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    CONSTRAINT delivery_proposal_shipment_unique UNIQUE (shipment_id),
    CONSTRAINT delivery_proposal_request_unique UNIQUE (business_id, client_request_id),
    CONSTRAINT delivery_proposal_fingerprint_shape CHECK (input_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT delivery_proposal_token_hash_shape CHECK (confirmation_token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT delivery_proposal_phone_e164 CHECK (recipient_phone ~ '^[+][1-9][0-9]{7,14}$'),
    CONSTRAINT delivery_proposal_mobile_channel_known CHECK (mobile_channel IN ('SMS', 'WHATSAPP')),
    CONSTRAINT delivery_proposal_status_known CHECK (status IN ('PROPOSED', 'ACCEPTED', 'EXPIRED')),
    CONSTRAINT delivery_proposal_expiry_valid CHECK (expires_at > created_at),
    CONSTRAINT delivery_proposal_state_consistent CHECK (
        (status IN ('PROPOSED', 'EXPIRED') AND accepted_at IS NULL)
        OR (status = 'ACCEPTED' AND accepted_at IS NOT NULL)
    )
);

CREATE INDEX delivery_proposal_business_status_idx
    ON delivery_proposal(business_id, status, created_at DESC);
