CREATE SEQUENCE evidence_ledger_sequence;

CREATE TABLE evidence_record (
    ledger_sequence BIGINT PRIMARY KEY DEFAULT nextval('evidence_ledger_sequence'),
    id UUID NOT NULL UNIQUE,
    event_id UUID NOT NULL UNIQUE,
    evidence_type VARCHAR(128) NOT NULL,
    subject_type VARCHAR(64) NOT NULL,
    subject_id UUID NOT NULL,
    shipment_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    actor VARCHAR(255),
    source VARCHAR(128) NOT NULL,
    correlation_id UUID NOT NULL,
    schema_version INTEGER NOT NULL,
    correction_of_id UUID REFERENCES evidence_record(id),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    payload_checksum CHAR(64) NOT NULL,
    previous_chain_hash CHAR(64),
    chain_hash CHAR(64),
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT evidence_type_shape CHECK (evidence_type ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT evidence_subject_type_shape CHECK (subject_type ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    CONSTRAINT evidence_source_shape CHECK (source ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT evidence_schema_version_positive CHECK (schema_version > 0),
    CONSTRAINT evidence_metadata_object CHECK (jsonb_typeof(metadata) = 'object'),
    CONSTRAINT evidence_payload_checksum_shape CHECK (payload_checksum ~ '^[0-9a-f]{64}$'),
    CONSTRAINT evidence_correction_not_self CHECK (correction_of_id IS NULL OR correction_of_id <> id),
    CONSTRAINT evidence_chain_complete CHECK (
        (previous_chain_hash IS NULL AND chain_hash IS NULL)
        OR (previous_chain_hash ~ '^[0-9a-f]{64}$' AND chain_hash ~ '^[0-9a-f]{64}$')
    ),
    CONSTRAINT evidence_recorded_after_occurrence CHECK (recorded_at >= occurred_at - INTERVAL '10 minutes')
);

CREATE INDEX evidence_shipment_chronology_idx
    ON evidence_record(shipment_id, occurred_at, ledger_sequence)
    WHERE shipment_id IS NOT NULL;

CREATE INDEX evidence_subject_chronology_idx
    ON evidence_record(subject_type, subject_id, occurred_at, ledger_sequence);

CREATE INDEX evidence_correlation_idx
    ON evidence_record(correlation_id, ledger_sequence);

CREATE TABLE evidence_file_reference (
    evidence_id UUID NOT NULL REFERENCES evidence_record(id),
    file_id UUID NOT NULL,
    sha256 CHAR(64) NOT NULL,
    PRIMARY KEY (evidence_id, file_id),
    CONSTRAINT evidence_file_checksum_shape CHECK (sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX evidence_file_id_idx
    ON evidence_file_reference(file_id);

CREATE FUNCTION reject_evidence_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'evidence is append-only; write a correction record instead'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER evidence_record_append_only
    BEFORE UPDATE OR DELETE ON evidence_record
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_mutation();

CREATE TRIGGER evidence_file_reference_append_only
    BEFORE UPDATE OR DELETE ON evidence_file_reference
    FOR EACH ROW EXECUTE FUNCTION reject_evidence_mutation();

REVOKE UPDATE, DELETE ON evidence_record FROM PUBLIC;
REVOKE UPDATE, DELETE ON evidence_file_reference FROM PUBLIC;

COMMENT ON TABLE evidence_record IS
    'Append-only business evidence. Corrections are new rows linked by correction_of_id.';
COMMENT ON COLUMN evidence_record.shipment_id IS
    'Deliberately has no operational foreign key so evidence survives source retention or removal.';
COMMENT ON TABLE evidence_file_reference IS
    'Immutable object reference and checksum; no file foreign key so missing/retained evidence remains explicit.';
