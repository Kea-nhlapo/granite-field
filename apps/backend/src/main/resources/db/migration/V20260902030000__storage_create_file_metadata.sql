CREATE TABLE stored_file (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business_profile(id),
    category VARCHAR(32) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    object_key VARCHAR(512) NOT NULL UNIQUE,
    content_type VARCHAR(128) NOT NULL,
    extension VARCHAR(16) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    scan_status VARCHAR(32) NOT NULL,
    storage_status VARCHAR(32) NOT NULL,
    uploaded_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    created_at TIMESTAMPTZ NOT NULL,
    stored_at TIMESTAMPTZ,
    CONSTRAINT stored_file_category_known CHECK (
        category IN ('INVOICE', 'DELIVERY_PROOF', 'COMPANY_DOCUMENT', 'INSURANCE_DOCUMENT')
    ),
    CONSTRAINT stored_file_content_type_known CHECK (
        content_type IN ('application/pdf', 'image/jpeg', 'image/png')
    ),
    CONSTRAINT stored_file_extension_known CHECK (
        extension IN ('pdf', 'jpg', 'jpeg', 'png')
    ),
    CONSTRAINT stored_file_size_positive CHECK (size_bytes > 0),
    CONSTRAINT stored_file_sha256_shape CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT stored_file_scan_status_known CHECK (
        scan_status IN ('CLEAN', 'INFECTED', 'ERROR')
    ),
    CONSTRAINT stored_file_storage_status_known CHECK (
        storage_status IN ('UPLOADING', 'AVAILABLE', 'FAILED')
    ),
    CONSTRAINT stored_file_availability_consistent CHECK (
        (storage_status = 'AVAILABLE' AND scan_status = 'CLEAN' AND stored_at IS NOT NULL)
        OR (storage_status IN ('UPLOADING', 'FAILED') AND stored_at IS NULL)
    )
);

CREATE INDEX stored_file_business_idx
    ON stored_file(business_id, created_at DESC);

CREATE INDEX stored_file_uploader_idx
    ON stored_file(uploaded_by_user_id, created_at DESC);
