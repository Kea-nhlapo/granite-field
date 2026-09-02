CREATE TABLE trust_score_snapshot (
    business_id UUID PRIMARY KEY REFERENCES business_profile(id) ON DELETE CASCADE,
    provisional_score NUMERIC(5, 2) NOT NULL,
    verified_score NUMERIC(5, 2) NOT NULL,
    verification_schedule_mode VARCHAR(40) NOT NULL,
    calculation_version VARCHAR(64) NOT NULL,
    source_evidence_through_sequence BIGINT NOT NULL,
    provisional_calculated_at TIMESTAMPTZ NOT NULL,
    verified_calculated_at TIMESTAMPTZ NOT NULL,
    next_verification_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT trust_score_values_valid CHECK (
        provisional_score BETWEEN 0 AND 100
        AND verified_score BETWEEN 0 AND 100
        AND source_evidence_through_sequence >= 0
    )
);

CREATE INDEX trust_score_verification_due_idx
    ON trust_score_snapshot(next_verification_at, business_id);

INSERT INTO trust_score_snapshot (
    business_id,
    provisional_score,
    verified_score,
    verification_schedule_mode,
    calculation_version,
    source_evidence_through_sequence,
    provisional_calculated_at,
    verified_calculated_at,
    next_verification_at
)
SELECT id,
       CASE verification_status WHEN 'REGISTRY_VERIFIED' THEN 65.00 ELSE 50.00 END,
       50.00,
       'COMPRESSED_DEMO',
       'trust-score/v1',
       0,
       NOW(),
       NOW(),
       NOW()
  FROM business_profile;

COMMENT ON TABLE trust_score_snapshot IS
    'Public-safe score projection. Sensitive evidence and individual risk signals remain in protected modules.';
COMMENT ON COLUMN trust_score_snapshot.verification_schedule_mode IS
    'Explicitly tells clients when the verified schedule is compressed for a demonstration.';
