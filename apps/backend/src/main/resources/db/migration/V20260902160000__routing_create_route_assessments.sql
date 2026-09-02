ALTER TABLE routing_candidate
    ADD CONSTRAINT routing_candidate_id_calculation_unique UNIQUE (id, calculation_id);

CREATE TABLE routing_assessment (
    id UUID PRIMARY KEY,
    requested_by_business_id UUID NOT NULL REFERENCES business_profile(id),
    calculation_id UUID NOT NULL REFERENCES routing_calculation(id),
    client_request_id UUID NOT NULL,
    input_fingerprint CHAR(64) NOT NULL,
    cargo_profile VARCHAR(64) NOT NULL,
    algorithm_version VARCHAR(64) NOT NULL,
    recommended_candidate_id UUID NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT routing_assessment_request_unique UNIQUE (requested_by_business_id, client_request_id),
    CONSTRAINT routing_assessment_fingerprint_shape CHECK (input_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT routing_assessment_recommended_candidate_fk
        FOREIGN KEY (recommended_candidate_id, calculation_id)
        REFERENCES routing_candidate(id, calculation_id)
);

CREATE INDEX routing_assessment_calculation_idx ON routing_assessment(calculation_id);

CREATE TABLE routing_assessment_weight (
    assessment_id UUID NOT NULL REFERENCES routing_assessment(id),
    factor VARCHAR(32) NOT NULL,
    weight NUMERIC(8, 6) NOT NULL,
    PRIMARY KEY (assessment_id, factor),
    CONSTRAINT routing_assessment_weight_factor_known CHECK (
        factor IN ('TIME', 'DISTANCE', 'FUEL', 'TOLLS', 'SAFETY_EXPOSURE', 'ROAD_QUALITY', 'CONNECTIVITY')
    ),
    CONSTRAINT routing_assessment_weight_value_valid CHECK (weight BETWEEN 0 AND 1)
);

CREATE TABLE routing_candidate_score (
    assessment_id UUID NOT NULL REFERENCES routing_assessment(id),
    candidate_id UUID NOT NULL,
    calculation_id UUID NOT NULL,
    total_score NUMERIC(8, 6) NOT NULL,
    confidence NUMERIC(8, 6) NOT NULL,
    PRIMARY KEY (assessment_id, candidate_id),
    CONSTRAINT routing_candidate_score_candidate_fk
        FOREIGN KEY (candidate_id, calculation_id)
        REFERENCES routing_candidate(id, calculation_id),
    CONSTRAINT routing_candidate_score_values_valid CHECK (
        total_score BETWEEN 0 AND 1 AND confidence BETWEEN 0 AND 1
    )
);

CREATE TABLE routing_factor_score (
    assessment_id UUID NOT NULL,
    candidate_id UUID NOT NULL,
    factor VARCHAR(32) NOT NULL,
    raw_value NUMERIC(20, 6),
    raw_unit VARCHAR(32) NOT NULL,
    normalized_value NUMERIC(8, 6) NOT NULL,
    weight NUMERIC(8, 6) NOT NULL,
    contribution NUMERIC(8, 6) NOT NULL,
    data_available BOOLEAN NOT NULL,
    PRIMARY KEY (assessment_id, candidate_id, factor),
    CONSTRAINT routing_factor_score_candidate_fk
        FOREIGN KEY (assessment_id, candidate_id)
        REFERENCES routing_candidate_score(assessment_id, candidate_id),
    CONSTRAINT routing_factor_score_factor_known CHECK (
        factor IN ('TIME', 'DISTANCE', 'FUEL', 'TOLLS', 'SAFETY_EXPOSURE', 'ROAD_QUALITY', 'CONNECTIVITY')
    ),
    CONSTRAINT routing_factor_score_values_valid CHECK (
        normalized_value BETWEEN 0 AND 1
        AND weight BETWEEN 0 AND 1
        AND contribution BETWEEN 0 AND 1
        AND (raw_value IS NULL OR raw_value >= 0)
        AND ((data_available AND raw_value IS NOT NULL) OR (NOT data_available AND raw_value IS NULL))
    )
);

CREATE TABLE routing_candidate_option (
    assessment_id UUID NOT NULL,
    candidate_id UUID NOT NULL,
    option_type VARCHAR(32) NOT NULL,
    PRIMARY KEY (assessment_id, option_type),
    CONSTRAINT routing_candidate_option_score_fk
        FOREIGN KEY (assessment_id, candidate_id)
        REFERENCES routing_candidate_score(assessment_id, candidate_id),
    CONSTRAINT routing_candidate_option_type_known CHECK (
        option_type IN ('FASTEST', 'LOWEST_COST', 'SAFEST', 'BEST_CONNECTIVITY', 'RECOMMENDED')
    )
);

CREATE TABLE routing_candidate_reason (
    assessment_id UUID NOT NULL,
    candidate_id UUID NOT NULL,
    sequence INTEGER NOT NULL,
    reason VARCHAR(255) NOT NULL,
    PRIMARY KEY (assessment_id, candidate_id, sequence),
    CONSTRAINT routing_candidate_reason_score_fk
        FOREIGN KEY (assessment_id, candidate_id)
        REFERENCES routing_candidate_score(assessment_id, candidate_id),
    CONSTRAINT routing_candidate_reason_sequence_nonnegative CHECK (sequence >= 0)
);
