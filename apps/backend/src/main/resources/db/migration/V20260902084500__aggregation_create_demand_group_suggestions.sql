CREATE TABLE demand_group_suggestion (
    id UUID PRIMARY KEY,
    requested_by_business_id UUID NOT NULL REFERENCES business_profile(id),
    anchor_order_id UUID NOT NULL REFERENCES procurement_order(id),
    client_request_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    algorithm_version VARCHAR(100) NOT NULL,
    input_fingerprint CHAR(64) NOT NULL,
    search_radius_meters DOUBLE PRECISION NOT NULL,
    maximum_distance_meters DOUBLE PRECISION NOT NULL,
    minimum_window_overlap_seconds BIGINT NOT NULL,
    minimum_cargo_overlap_ratio NUMERIC(6, 5) NOT NULL,
    candidate_limit INTEGER NOT NULL,
    score NUMERIC(6, 5) NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT demand_group_client_request_unique UNIQUE (requested_by_business_id, client_request_id),
    CONSTRAINT demand_group_status_known CHECK (status IN ('ACTIVE', 'NO_MATCH')),
    CONSTRAINT demand_group_fingerprint_shape CHECK (input_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT demand_group_thresholds_valid CHECK (
        search_radius_meters >= maximum_distance_meters
        AND maximum_distance_meters > 0
        AND minimum_window_overlap_seconds >= 0
        AND minimum_cargo_overlap_ratio > 0
        AND minimum_cargo_overlap_ratio <= 1
        AND candidate_limit > 0
    ),
    CONSTRAINT demand_group_score_valid CHECK (score >= 0 AND score <= 1)
);

CREATE UNIQUE INDEX demand_group_active_orders_unique
    ON demand_group_suggestion(requested_by_business_id, input_fingerprint)
    WHERE status = 'ACTIVE';

CREATE INDEX demand_group_business_created_idx
    ON demand_group_suggestion(requested_by_business_id, created_at DESC);

CREATE TABLE demand_group_order_evaluation (
    suggestion_id UUID NOT NULL REFERENCES demand_group_suggestion(id),
    order_id UUID NOT NULL REFERENCES procurement_order(id),
    buyer_business_id UUID NOT NULL REFERENCES business_profile(id),
    role VARCHAR(16) NOT NULL,
    included BOOLEAN NOT NULL,
    destination_label VARCHAR(500) NOT NULL,
    distance_meters DOUBLE PRECISION NOT NULL,
    window_overlap_seconds BIGINT NOT NULL,
    cargo_overlap_ratio NUMERIC(6, 5) NOT NULL,
    score NUMERIC(6, 5) NOT NULL,
    PRIMARY KEY (suggestion_id, order_id),
    CONSTRAINT demand_group_order_role_known CHECK (role IN ('ANCHOR', 'CANDIDATE')),
    CONSTRAINT demand_group_order_metrics_valid CHECK (
        distance_meters >= 0
        AND window_overlap_seconds >= 0
        AND cargo_overlap_ratio >= 0
        AND cargo_overlap_ratio <= 1
        AND score >= 0
        AND score <= 1
    )
);

CREATE INDEX demand_group_evaluation_order_idx
    ON demand_group_order_evaluation(order_id, included);

CREATE TABLE demand_group_constraint_result (
    suggestion_id UUID NOT NULL,
    order_id UUID NOT NULL,
    constraint_code VARCHAR(64) NOT NULL,
    outcome VARCHAR(8) NOT NULL,
    exclusion_reason VARCHAR(64),
    explanation VARCHAR(500) NOT NULL,
    PRIMARY KEY (suggestion_id, order_id, constraint_code),
    FOREIGN KEY (suggestion_id, order_id)
        REFERENCES demand_group_order_evaluation(suggestion_id, order_id),
    CONSTRAINT demand_group_constraint_known CHECK (constraint_code IN (
        'ANCHOR_ORDER',
        'SUPPLIER_OR_PICKUP_COMPATIBLE',
        'WITHIN_DISTANCE',
        'DELIVERY_WINDOW_OVERLAP',
        'CARGO_COMPATIBLE'
    )),
    CONSTRAINT demand_group_constraint_outcome_known CHECK (outcome IN ('PASS', 'FAIL')),
    CONSTRAINT demand_group_exclusion_reason_known CHECK (exclusion_reason IS NULL OR exclusion_reason IN (
        'SUPPLIER_OR_PICKUP_MISMATCH',
        'DISTANCE_EXCEEDS_LIMIT',
        'DELIVERY_WINDOWS_DO_NOT_OVERLAP',
        'CARGO_PROFILE_UNAVAILABLE',
        'CARGO_NOT_COMPATIBLE'
    )),
    CONSTRAINT demand_group_constraint_reason_consistent CHECK (
        (outcome = 'PASS' AND exclusion_reason IS NULL)
        OR (outcome = 'FAIL' AND exclusion_reason IS NOT NULL)
    )
);
