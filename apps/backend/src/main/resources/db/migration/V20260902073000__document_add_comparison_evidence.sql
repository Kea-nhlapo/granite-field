CREATE TABLE document_comparison (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business_profile(id),
    client_request_id UUID NOT NULL,
    rule_set_version VARCHAR(64) NOT NULL,
    reference_document_id UUID NOT NULL REFERENCES document_record(id),
    reference_document_type VARCHAR(32) NOT NULL,
    reference_confirmation_id UUID NOT NULL REFERENCES document_confirmation(id),
    reference_confirmation_revision INTEGER NOT NULL,
    compared_document_id UUID NOT NULL REFERENCES document_record(id),
    compared_document_type VARCHAR(32) NOT NULL,
    compared_confirmation_id UUID NOT NULL REFERENCES document_confirmation(id),
    compared_confirmation_revision INTEGER NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT document_comparison_request_unique UNIQUE (business_id, client_request_id),
    CONSTRAINT document_comparison_scope_unique UNIQUE (
        business_id, reference_confirmation_id, compared_confirmation_id, rule_set_version
    ),
    CONSTRAINT document_comparison_sources_different CHECK (reference_document_id <> compared_document_id),
    CONSTRAINT document_comparison_revisions_positive CHECK (
        reference_confirmation_revision > 0 AND compared_confirmation_revision > 0
    ),
    CONSTRAINT document_comparison_reference_type_known CHECK (
        reference_document_type IN ('PURCHASE_ORDER', 'QUOTE', 'INVOICE', 'DELIVERY_NOTE')
    ),
    CONSTRAINT document_comparison_compared_type_known CHECK (
        compared_document_type IN ('PURCHASE_ORDER', 'QUOTE', 'INVOICE', 'DELIVERY_NOTE')
    )
);

CREATE INDEX document_comparison_business_created_idx
    ON document_comparison(business_id, created_at DESC);

CREATE TABLE document_mismatch_indicator (
    id UUID PRIMARY KEY,
    comparison_id UUID NOT NULL REFERENCES document_comparison(id),
    rule_code VARCHAR(64) NOT NULL,
    rule_version INTEGER NOT NULL,
    field_path VARCHAR(255) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    reference_document_id UUID NOT NULL REFERENCES document_record(id),
    reference_value VARCHAR(4000),
    compared_document_id UUID NOT NULL REFERENCES document_record(id),
    compared_value VARCHAR(4000),
    explanation VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT document_mismatch_scope_unique UNIQUE (comparison_id, rule_code, field_path),
    CONSTRAINT document_mismatch_rule_version_positive CHECK (rule_version > 0),
    CONSTRAINT document_mismatch_severity_known CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT document_mismatch_rule_known CHECK (
        rule_code IN (
            'DOCUMENT_QUANTITY_MISMATCH',
            'DOCUMENT_PRICE_MISMATCH',
            'DOCUMENT_SUPPLIER_MISMATCH',
            'DOCUMENT_CUSTOMER_MISMATCH',
            'DOCUMENT_DESTINATION_MISMATCH',
            'DOCUMENT_DATE_MISMATCH',
            'DUPLICATE_DOCUMENT_CONTENT'
        )
    )
);

CREATE INDEX document_mismatch_comparison_idx
    ON document_mismatch_indicator(comparison_id, severity, rule_code);
