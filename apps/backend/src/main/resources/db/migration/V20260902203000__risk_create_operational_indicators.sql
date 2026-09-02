CREATE TABLE risk_indicator (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipment_record(id),
    business_id UUID NOT NULL REFERENCES business_profile(id),
    rule_code VARCHAR(64) NOT NULL,
    rule_version VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    explanation VARCHAR(1000) NOT NULL,
    state VARCHAR(24) NOT NULL,
    first_observed_at TIMESTAMPTZ NOT NULL,
    last_observed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT risk_indicator_rule_known CHECK (rule_code IN (
        'ROUTE_DEVIATION', 'UNEXPECTED_STOP', 'TRACKER_OFFLINE',
        'STATIONARY_FUEL_DROP', 'UNEXPECTED_SEAL_OPENING',
        'DELIVERY_DELAY', 'DRIVER_ASSIGNMENT_CHANGED', 'TELEMETRY_DEVICE_CHANGED'
    )),
    CONSTRAINT risk_indicator_severity_known CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT risk_indicator_state_known CHECK (state IN (
        'OPEN', 'ACKNOWLEDGED', 'INVESTIGATING', 'RESOLVED', 'FALSE_POSITIVE'
    )),
    CONSTRAINT risk_indicator_time_order CHECK (
        last_observed_at >= first_observed_at
        AND updated_at >= created_at
    )
);

CREATE UNIQUE INDEX risk_one_active_rule_per_shipment_idx
    ON risk_indicator(shipment_id, rule_code)
    WHERE state IN ('OPEN', 'ACKNOWLEDGED', 'INVESTIGATING');

CREATE INDEX risk_indicator_shipment_time_idx
    ON risk_indicator(shipment_id, last_observed_at DESC);

CREATE TABLE risk_indicator_evidence (
    indicator_id UUID NOT NULL REFERENCES risk_indicator(id),
    evidence_type VARCHAR(32) NOT NULL,
    evidence_id UUID NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (indicator_id, evidence_type, evidence_id),
    CONSTRAINT risk_evidence_type_known CHECK (evidence_type IN (
        'TELEMETRY_READING', 'TELEMETRY_DEVICE', 'SHIPMENT', 'SHIPMENT_ASSIGNMENT'
    ))
);

CREATE INDEX risk_evidence_reference_idx
    ON risk_indicator_evidence(evidence_type, evidence_id);

CREATE TABLE risk_indicator_transition (
    id UUID PRIMARY KEY,
    indicator_id UUID NOT NULL REFERENCES risk_indicator(id),
    command_id UUID NOT NULL UNIQUE,
    input_fingerprint CHAR(64) NOT NULL,
    sequence INTEGER NOT NULL,
    from_state VARCHAR(24),
    to_state VARCHAR(24) NOT NULL,
    actor_user_id UUID REFERENCES access_user_account(id),
    note VARCHAR(1000) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT risk_transition_sequence_unique UNIQUE (indicator_id, sequence),
    CONSTRAINT risk_transition_fingerprint_shape CHECK (input_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT risk_transition_from_state_known CHECK (
        from_state IS NULL OR from_state IN ('OPEN', 'ACKNOWLEDGED', 'INVESTIGATING')
    ),
    CONSTRAINT risk_transition_to_state_known CHECK (to_state IN (
        'OPEN', 'ACKNOWLEDGED', 'INVESTIGATING', 'RESOLVED', 'FALSE_POSITIVE'
    )),
    CONSTRAINT risk_transition_initial_shape CHECK (
        (sequence = 0 AND from_state IS NULL AND to_state = 'OPEN')
        OR (sequence > 0 AND from_state IS NOT NULL AND to_state <> 'OPEN')
    )
);
