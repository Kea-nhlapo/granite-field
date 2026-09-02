CREATE TABLE insurance_case (
    id UUID PRIMARY KEY,
    client_request_id UUID NOT NULL,
    input_fingerprint CHAR(64) NOT NULL,
    shipment_id UUID NOT NULL,
    business_id UUID NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    assigned_insurer_user_id UUID NOT NULL,
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT insurance_case_request_unique UNIQUE (created_by_user_id, client_request_id),
    CONSTRAINT insurance_case_fingerprint_shape CHECK (input_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT insurance_case_purpose_known CHECK (
        purpose IN ('CLAIM_REVIEW', 'LOSS_INVESTIGATION', 'CARGO_RISK_REVIEW')
    )
);

CREATE INDEX insurance_case_assignee_idx
    ON insurance_case(assigned_insurer_user_id, created_at DESC);

CREATE INDEX insurance_case_shipment_idx
    ON insurance_case(shipment_id, created_at DESC);

CREATE TABLE insurance_evidence_access_audit (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL,
    shipment_id UUID,
    actor_user_id UUID NOT NULL,
    purpose VARCHAR(32),
    outcome VARCHAR(16) NOT NULL,
    reason VARCHAR(128) NOT NULL,
    correlation_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT insurance_access_purpose_known CHECK (
        purpose IS NULL OR purpose IN ('CLAIM_REVIEW', 'LOSS_INVESTIGATION', 'CARGO_RISK_REVIEW')
    ),
    CONSTRAINT insurance_access_outcome_known CHECK (outcome IN ('GRANTED', 'DENIED')),
    CONSTRAINT insurance_access_shape CHECK (
        (outcome = 'GRANTED' AND shipment_id IS NOT NULL AND purpose IS NOT NULL)
        OR outcome = 'DENIED'
    )
);

CREATE INDEX insurance_access_case_time_idx
    ON insurance_evidence_access_audit(case_id, occurred_at DESC);

CREATE INDEX insurance_access_actor_time_idx
    ON insurance_evidence_access_audit(actor_user_id, occurred_at DESC);

CREATE TABLE insurance_case_decision (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES insurance_case(id),
    command_id UUID NOT NULL UNIQUE,
    input_fingerprint CHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    note VARCHAR(1000) NOT NULL,
    decided_by_user_id UUID NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT insurance_decision_fingerprint_shape CHECK (input_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT insurance_decision_outcome_known CHECK (
        outcome IN ('DEMO_APPROVED', 'DEMO_DECLINED', 'NEEDS_MORE_EVIDENCE')
    )
);

CREATE INDEX insurance_decision_case_time_idx
    ON insurance_case_decision(case_id, decided_at, id);

CREATE FUNCTION reject_insurance_audit_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'insurance audit records are append-only'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER insurance_access_append_only
    BEFORE UPDATE OR DELETE ON insurance_evidence_access_audit
    FOR EACH ROW EXECUTE FUNCTION reject_insurance_audit_mutation();

CREATE TRIGGER insurance_decision_append_only
    BEFORE UPDATE OR DELETE ON insurance_case_decision
    FOR EACH ROW EXECUTE FUNCTION reject_insurance_audit_mutation();

REVOKE UPDATE, DELETE ON insurance_evidence_access_audit FROM PUBLIC;
REVOKE UPDATE, DELETE ON insurance_case_decision FROM PUBLIC;

COMMENT ON TABLE insurance_case IS
    'Purpose-scoped assignment granting one insurer access to one shipment case.';
COMMENT ON TABLE insurance_evidence_access_audit IS
    'Append-only record of granted and denied attempts to view shipment evidence.';
COMMENT ON TABLE insurance_case_decision IS
    'Demo-only case outcomes; not underwriting, pricing, or a policy decision.';
