CREATE TABLE trust_public_summary (
    business_id UUID PRIMARY KEY REFERENCES business_profile(id) ON DELETE CASCADE,
    registry_verified BOOLEAN NOT NULL,
    identity_verified BOOLEAN NOT NULL,
    completed_transaction_count INTEGER NOT NULL,
    successful_delivery_count INTEGER NOT NULL,
    delivery_success_rate NUMERIC(5, 4),
    average_rating NUMERIC(2, 1),
    rating_count INTEGER NOT NULL,
    history_band VARCHAR(40) NOT NULL,
    calculation_version VARCHAR(64) NOT NULL,
    source_evidence_through_sequence BIGINT NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT trust_counts_valid CHECK (
        completed_transaction_count >= 0
        AND successful_delivery_count BETWEEN 0 AND completed_transaction_count
        AND rating_count >= 0
        AND source_evidence_through_sequence >= 0
    ),
    CONSTRAINT trust_delivery_rate_valid CHECK (
        (completed_transaction_count = 0 AND delivery_success_rate IS NULL)
        OR (completed_transaction_count > 0 AND delivery_success_rate BETWEEN 0 AND 1)
    ),
    CONSTRAINT trust_rating_valid CHECK (
        (rating_count = 0 AND average_rating IS NULL)
        OR (rating_count > 0 AND average_rating BETWEEN 1 AND 5)
    ),
    CONSTRAINT trust_history_band_known CHECK (history_band IN (
        'NO_COMPLETED_HISTORY', 'LIMITED_COMPLETED_HISTORY', 'ESTABLISHED_COMPLETED_HISTORY'
    ))
);

INSERT INTO trust_public_summary (
    business_id, registry_verified, identity_verified, completed_transaction_count,
    successful_delivery_count, delivery_success_rate, average_rating, rating_count,
    history_band, calculation_version, source_evidence_through_sequence, calculated_at
)
SELECT id, verification_status = 'REGISTRY_VERIFIED', FALSE, 0,
       0, NULL, NULL, 0, 'NO_COMPLETED_HISTORY', 'public-trust/v1', 0, NOW()
  FROM business_profile;

COMMENT ON TABLE trust_public_summary IS
    'Rebuildable public facts only. Internal risk, claims, device data, and investigation notes are prohibited.';
COMMENT ON COLUMN trust_public_summary.average_rating IS
    'Null until a verified post-transaction rating source is introduced; never inferred from risk data.';
COMMENT ON COLUMN trust_public_summary.calculation_version IS
    'public-trust/v1: latest evidenced DELIVERED or DISPUTED outcomes; cancelled shipments are excluded.';
