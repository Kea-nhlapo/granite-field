ALTER TABLE handover_challenge
    ADD COLUMN expected_quantity NUMERIC(19, 4),
    ADD COLUMN expected_unit_of_measure VARCHAR(32),
    ADD CONSTRAINT handover_expected_quantity_complete CHECK (
        (expected_quantity IS NULL AND expected_unit_of_measure IS NULL)
        OR (expected_quantity > 0 AND expected_unit_of_measure IS NOT NULL)
    );

ALTER TABLE handover_confirmation
    ADD COLUMN captured_quantity NUMERIC(19, 4),
    ADD COLUMN photo_url VARCHAR(2048),
    ADD CONSTRAINT handover_captured_quantity_valid CHECK (
        captured_quantity IS NULL OR captured_quantity >= 0
    );

CREATE TABLE handover_delivery_resolution (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipment_record(id),
    business_id UUID NOT NULL REFERENCES business_profile(id),
    command_id UUID NOT NULL UNIQUE,
    input_fingerprint CHAR(64) NOT NULL,
    resolved_amount NUMERIC(19, 4) NOT NULL,
    resolved_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    resolved_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT handover_delivery_resolution_once UNIQUE (shipment_id, business_id),
    CONSTRAINT handover_delivery_resolution_fingerprint_shape
        CHECK (input_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT handover_delivery_resolution_amount_positive CHECK (resolved_amount > 0)
);

CREATE INDEX handover_delivery_resolution_business_time_idx
    ON handover_delivery_resolution(business_id, resolved_at DESC);
