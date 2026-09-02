CREATE TABLE handover_challenge (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipment_record(id),
    business_id UUID NOT NULL REFERENCES business_profile(id),
    handover_type VARCHAR(16) NOT NULL,
    delivery_order_id UUID,
    state VARCHAR(16) NOT NULL,
    nonce_hash CHAR(64) NOT NULL UNIQUE,
    initiator_user_id UUID NOT NULL REFERENCES access_user_account(id),
    counterparty_user_id UUID NOT NULL REFERENCES access_user_account(id),
    expected_location_label VARCHAR(500) NOT NULL,
    expected_location GEOGRAPHY(POINT, 4326) NOT NULL,
    location_tolerance_metres INTEGER NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    correlation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT handover_type_known CHECK (handover_type IN ('COLLECTION', 'DELIVERY')),
    CONSTRAINT handover_state_known CHECK (state IN ('PENDING', 'COMPLETED', 'DISPUTED', 'EXPIRED')),
    CONSTRAINT handover_delivery_shape CHECK (
        (handover_type = 'COLLECTION' AND delivery_order_id IS NULL)
        OR (handover_type = 'DELIVERY' AND delivery_order_id IS NOT NULL)
    ),
    CONSTRAINT handover_parties_distinct CHECK (initiator_user_id <> counterparty_user_id),
    CONSTRAINT handover_nonce_hash_shape CHECK (nonce_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT handover_location_tolerance_valid CHECK (location_tolerance_metres BETWEEN 1 AND 10000),
    CONSTRAINT handover_time_order CHECK (
        expires_at > created_at
        AND ((state = 'PENDING' AND completed_at IS NULL)
            OR (state <> 'PENDING' AND completed_at IS NOT NULL AND completed_at >= created_at))
    ),
    CONSTRAINT handover_delivery_order_fk
        FOREIGN KEY (shipment_id, delivery_order_id)
        REFERENCES shipment_load_order(shipment_id, order_id)
);

CREATE UNIQUE INDEX handover_one_active_target_idx
    ON handover_challenge (
        shipment_id,
        handover_type,
        COALESCE(delivery_order_id, '00000000-0000-0000-0000-000000000000'::UUID)
    )
    WHERE state = 'PENDING';

CREATE INDEX handover_shipment_created_idx
    ON handover_challenge(shipment_id, created_at DESC);

CREATE INDEX handover_expected_location_gix
    ON handover_challenge USING GIST(expected_location);

CREATE TABLE handover_confirmation (
    id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL REFERENCES handover_challenge(id),
    command_id UUID NOT NULL UNIQUE,
    input_fingerprint CHAR(64) NOT NULL,
    actor_user_id UUID NOT NULL REFERENCES access_user_account(id),
    party VARCHAR(16) NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    distance_metres DOUBLE PRECISION NOT NULL,
    quantity_outcome VARCHAR(16) NOT NULL,
    quantity_note VARCHAR(500) NOT NULL,
    CONSTRAINT handover_confirmation_actor_unique UNIQUE (challenge_id, actor_user_id),
    CONSTRAINT handover_confirmation_party_unique UNIQUE (challenge_id, party),
    CONSTRAINT handover_confirmation_fingerprint_shape CHECK (input_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT handover_confirmation_party_known CHECK (party IN ('INITIATOR', 'COUNTERPARTY')),
    CONSTRAINT handover_confirmation_quantity_known CHECK (quantity_outcome IN ('MATCHED', 'DISPUTED')),
    CONSTRAINT handover_confirmation_distance_valid CHECK (distance_metres >= 0),
    CONSTRAINT handover_confirmation_time_order CHECK (received_at >= observed_at - INTERVAL '10 minutes')
);

CREATE INDEX handover_confirmation_challenge_time_idx
    ON handover_confirmation(challenge_id, received_at);

CREATE TABLE handover_attempt (
    id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL REFERENCES handover_challenge(id),
    actor_user_id UUID NOT NULL REFERENCES access_user_account(id),
    outcome VARCHAR(40) NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL,
    observed_at TIMESTAMPTZ,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    detail VARCHAR(500) NOT NULL,
    CONSTRAINT handover_attempt_outcome_known CHECK (outcome IN (
        'ACCEPTED', 'OFFLINE_NOT_ALLOWED', 'CLOCK_SKEW_EXCEEDED',
        'OUTSIDE_LOCATION_TOLERANCE', 'PARTICIPANT_MISMATCH',
        'PARTY_ALREADY_CONFIRMED', 'CHALLENGE_REPLAYED',
        'CHALLENGE_EXPIRED', 'SHIPMENT_STATE_CONFLICT'
    )),
    CONSTRAINT handover_attempt_coordinates_complete CHECK (
        (latitude IS NULL) = (longitude IS NULL)
        AND (latitude IS NULL OR (
            latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180
        ))
    )
);

CREATE INDEX handover_attempt_challenge_time_idx
    ON handover_attempt(challenge_id, attempted_at);
