CREATE TABLE transport_capacity_match_search (
    id UUID PRIMARY KEY,
    requested_by_business_id UUID NOT NULL REFERENCES business_profile(id),
    client_request_id UUID NOT NULL,
    demand_group_suggestion_id UUID NOT NULL,
    input_fingerprint CHAR(64) NOT NULL,
    algorithm_version VARCHAR(64) NOT NULL,
    required_weight_kg NUMERIC(15, 3) NOT NULL,
    required_volume_cubic_metres NUMERIC(15, 3) NOT NULL,
    delivery_window_start TIMESTAMPTZ NOT NULL,
    delivery_window_end TIMESTAMPTZ NOT NULL,
    order_count INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT transport_match_request_unique UNIQUE (requested_by_business_id, client_request_id),
    CONSTRAINT transport_match_fingerprint_shape CHECK (input_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT transport_match_capacity_positive CHECK (
        required_weight_kg > 0 AND required_volume_cubic_metres > 0
    ),
    CONSTRAINT transport_match_window_valid CHECK (delivery_window_end > delivery_window_start),
    CONSTRAINT transport_match_order_count_valid CHECK (order_count >= 2),
    CONSTRAINT transport_match_status_known CHECK (
        status IN ('MATCHED', 'NO_MATCH', 'RESERVED', 'RELEASED', 'EXPIRED')
    )
);

CREATE INDEX transport_capacity_match_search_group_idx
    ON transport_capacity_match_search(requested_by_business_id, demand_group_suggestion_id, created_at DESC);

CREATE TABLE transport_capacity_match_cargo_trait (
    match_search_id UUID NOT NULL REFERENCES transport_capacity_match_search(id),
    cargo_trait VARCHAR(64) NOT NULL,
    PRIMARY KEY (match_search_id, cargo_trait),
    CONSTRAINT transport_match_cargo_trait_known CHECK (cargo_trait IN (
        'DRY_GOODS', 'FOOD_GRADE', 'HAZARDOUS_GOODS', 'HIGH_VALUE', 'TEMPERATURE_CONTROLLED'
    ))
);

CREATE TABLE transport_capacity_match_candidate (
    match_search_id UUID NOT NULL REFERENCES transport_capacity_match_search(id),
    offer_id UUID NOT NULL REFERENCES transport_capacity_offer(id),
    transporter_id UUID NOT NULL REFERENCES transport_transporter(id),
    compatible BOOLEAN NOT NULL,
    candidate_rank INTEGER,
    available_weight_kg NUMERIC(15, 3) NOT NULL,
    available_volume_cubic_metres NUMERIC(15, 3) NOT NULL,
    added_distance_metres DOUBLE PRECISION NOT NULL,
    timing_overlap_seconds BIGINT NOT NULL,
    estimated_cost_zar NUMERIC(15, 2) NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (match_search_id, offer_id),
    CONSTRAINT transport_match_candidate_rank_valid CHECK (
        (compatible AND candidate_rank IS NOT NULL AND candidate_rank > 0)
        OR (NOT compatible AND candidate_rank IS NULL)
    ),
    CONSTRAINT transport_match_candidate_values_valid CHECK (
        available_weight_kg >= 0
        AND available_volume_cubic_metres >= 0
        AND added_distance_metres >= 0
        AND timing_overlap_seconds >= 0
        AND estimated_cost_zar >= 0
        AND score >= 0 AND score <= 1
    )
);

CREATE TABLE transport_capacity_match_constraint_result (
    match_search_id UUID NOT NULL,
    offer_id UUID NOT NULL,
    constraint_code VARCHAR(32) NOT NULL,
    outcome VARCHAR(8) NOT NULL,
    explanation VARCHAR(500) NOT NULL,
    PRIMARY KEY (match_search_id, offer_id, constraint_code),
    CONSTRAINT transport_match_constraint_candidate_fk
        FOREIGN KEY (match_search_id, offer_id)
        REFERENCES transport_capacity_match_candidate(match_search_id, offer_id),
    CONSTRAINT transport_match_constraint_known CHECK (constraint_code IN (
        'WEIGHT_CAPACITY', 'VOLUME_CAPACITY', 'CARGO_RESTRICTIONS',
        'DELIVERY_WINDOW', 'ROUTE_CORRIDOR'
    )),
    CONSTRAINT transport_match_constraint_outcome_known CHECK (outcome IN ('PASS', 'FAIL'))
);

CREATE TABLE transport_capacity_match_score_component (
    match_search_id UUID NOT NULL,
    offer_id UUID NOT NULL,
    component_code VARCHAR(32) NOT NULL,
    raw_value DOUBLE PRECISION NOT NULL,
    normalized_value DOUBLE PRECISION NOT NULL,
    component_weight DOUBLE PRECISION NOT NULL,
    contribution DOUBLE PRECISION NOT NULL,
    explanation VARCHAR(500) NOT NULL,
    PRIMARY KEY (match_search_id, offer_id, component_code),
    CONSTRAINT transport_match_score_candidate_fk
        FOREIGN KEY (match_search_id, offer_id)
        REFERENCES transport_capacity_match_candidate(match_search_id, offer_id),
    CONSTRAINT transport_match_score_values_valid CHECK (
        normalized_value >= 0 AND normalized_value <= 1
        AND component_weight >= 0 AND component_weight <= 1
        AND contribution >= 0 AND contribution <= 1
    )
);

CREATE TABLE transport_capacity_reservation (
    id UUID PRIMARY KEY,
    match_search_id UUID NOT NULL UNIQUE REFERENCES transport_capacity_match_search(id),
    client_request_id UUID NOT NULL,
    offer_id UUID NOT NULL REFERENCES transport_capacity_offer(id),
    reserved_weight_kg NUMERIC(15, 3) NOT NULL,
    reserved_volume_cubic_metres NUMERIC(15, 3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    created_at TIMESTAMPTZ NOT NULL,
    released_at TIMESTAMPTZ,
    CONSTRAINT transport_reservation_request_unique UNIQUE (match_search_id, client_request_id),
    CONSTRAINT transport_reservation_capacity_positive CHECK (
        reserved_weight_kg > 0 AND reserved_volume_cubic_metres > 0
    ),
    CONSTRAINT transport_reservation_status_known CHECK (status IN ('ACTIVE', 'RELEASED', 'EXPIRED')),
    CONSTRAINT transport_reservation_times_consistent CHECK (
        expires_at > created_at
        AND ((status = 'ACTIVE' AND released_at IS NULL)
            OR (status IN ('RELEASED', 'EXPIRED') AND released_at IS NOT NULL))
    )
);

CREATE INDEX transport_capacity_reservation_expiry_idx
    ON transport_capacity_reservation(expires_at)
    WHERE status = 'ACTIVE';
