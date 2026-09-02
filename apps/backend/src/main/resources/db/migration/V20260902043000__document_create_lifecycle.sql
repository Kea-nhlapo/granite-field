CREATE TABLE document_record (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business_profile(id),
    stored_file_id UUID NOT NULL UNIQUE REFERENCES stored_file(id),
    client_request_id UUID NOT NULL,
    document_type VARCHAR(32) NOT NULL,
    state VARCHAR(16) NOT NULL,
    processing_attempts INTEGER NOT NULL DEFAULT 0,
    processing_token UUID,
    processing_started_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT document_request_unique UNIQUE (business_id, client_request_id),
    CONSTRAINT document_type_known CHECK (
        document_type IN (
            'PURCHASE_ORDER', 'QUOTE', 'INVOICE', 'DELIVERY_NOTE',
            'INSURANCE_DOCUMENT', 'COMPANY_DOCUMENT'
        )
    ),
    CONSTRAINT document_state_known CHECK (
        state IN ('UPLOADED', 'QUEUED', 'PROCESSING', 'PARSED', 'FAILED', 'CONFIRMED')
    ),
    CONSTRAINT document_attempts_non_negative CHECK (processing_attempts >= 0),
    CONSTRAINT document_processing_claim_consistent CHECK (
        (state = 'PROCESSING' AND processing_token IS NOT NULL AND processing_started_at IS NOT NULL)
        OR (state <> 'PROCESSING' AND processing_token IS NULL AND processing_started_at IS NULL)
    )
);

CREATE INDEX document_business_created_idx
    ON document_record(business_id, created_at DESC);

CREATE INDEX document_processing_idx
    ON document_record(state, processing_started_at)
    WHERE state IN ('QUEUED', 'FAILED', 'PROCESSING');

CREATE TABLE document_state_transition (
    id BIGSERIAL PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES document_record(id),
    from_state VARCHAR(16),
    to_state VARCHAR(16) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT document_transition_from_known CHECK (
        from_state IS NULL OR from_state IN ('UPLOADED', 'QUEUED', 'PROCESSING', 'PARSED', 'FAILED', 'CONFIRMED')
    ),
    CONSTRAINT document_transition_to_known CHECK (
        to_state IN ('UPLOADED', 'QUEUED', 'PROCESSING', 'PARSED', 'FAILED', 'CONFIRMED')
    )
);

CREATE INDEX document_transition_timeline_idx
    ON document_state_transition(document_id, occurred_at, id);

CREATE TABLE document_extraction (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL UNIQUE REFERENCES document_record(id),
    provider_name VARCHAR(128) NOT NULL,
    parser_version VARCHAR(128) NOT NULL,
    raw_result_reference VARCHAR(512) NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE document_extracted_field (
    extraction_id UUID NOT NULL REFERENCES document_extraction(id),
    field_path VARCHAR(255) NOT NULL,
    field_value VARCHAR(4000) NOT NULL,
    confidence NUMERIC(5, 4) NOT NULL,
    source_page INTEGER,
    source_region VARCHAR(512),
    PRIMARY KEY (extraction_id, field_path),
    CONSTRAINT document_field_confidence_range CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT document_field_page_positive CHECK (source_page IS NULL OR source_page > 0)
);

CREATE TABLE document_confirmation (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES document_record(id),
    request_id UUID NOT NULL,
    revision INTEGER NOT NULL,
    confirmed_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT document_confirmation_request_unique UNIQUE (document_id, request_id),
    CONSTRAINT document_confirmation_revision_unique UNIQUE (document_id, revision),
    CONSTRAINT document_confirmation_revision_positive CHECK (revision > 0)
);

CREATE INDEX document_confirmation_latest_idx
    ON document_confirmation(document_id, revision DESC);

CREATE TABLE document_confirmed_field (
    confirmation_id UUID NOT NULL REFERENCES document_confirmation(id),
    field_path VARCHAR(255) NOT NULL,
    field_value VARCHAR(4000) NOT NULL,
    PRIMARY KEY (confirmation_id, field_path)
);
