CREATE TABLE notification_preference (
    user_id UUID NOT NULL REFERENCES access_user_account(id),
    category VARCHAR(32) NOT NULL,
    email_enabled BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, category),
    CONSTRAINT notification_preference_category_known CHECK (category IN (
        'SUPPLIER_INVITATION',
        'PROCUREMENT_UPDATE',
        'SHIPMENT_UPDATE',
        'SECURITY_ALERT'
    ))
);

CREATE TABLE email_notification (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    request_fingerprint CHAR(64) NOT NULL,
    recipient_email VARCHAR(320) NOT NULL,
    recipient_user_id UUID REFERENCES access_user_account(id),
    category VARCHAR(32) NOT NULL,
    template_key VARCHAR(100) NOT NULL,
    template_version INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    CONSTRAINT email_notification_fingerprint_shape CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT email_notification_category_known CHECK (category IN (
        'SUPPLIER_INVITATION',
        'PROCUREMENT_UPDATE',
        'SHIPMENT_UPDATE',
        'SECURITY_ALERT'
    )),
    CONSTRAINT email_notification_template_version_valid CHECK (template_version > 0),
    CONSTRAINT email_notification_status_known CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SUPPRESSED')),
    CONSTRAINT email_notification_terminal_times_consistent CHECK (
        (status = 'SENT' AND sent_at IS NOT NULL AND failed_at IS NULL)
        OR (status = 'FAILED' AND failed_at IS NOT NULL AND sent_at IS NULL)
        OR (status IN ('PENDING', 'SUPPRESSED') AND sent_at IS NULL AND failed_at IS NULL)
    )
);

CREATE INDEX email_notification_recipient_status_idx
    ON email_notification(recipient_user_id, status, created_at DESC);

CREATE TABLE email_notification_template_data (
    notification_id UUID NOT NULL REFERENCES email_notification(id),
    data_key VARCHAR(100) NOT NULL,
    data_value VARCHAR(4000) NOT NULL,
    PRIMARY KEY (notification_id, data_key)
);

CREATE TABLE email_delivery_attempt (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL REFERENCES email_notification(id),
    outbox_message_id UUID NOT NULL REFERENCES outbox_message(id),
    attempt_number INTEGER NOT NULL,
    provider_key VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL,
    provider_message_id VARCHAR(200),
    failure_code VARCHAR(64),
    failure_message VARCHAR(500),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT email_delivery_attempt_unique UNIQUE (notification_id, outbox_message_id, attempt_number),
    CONSTRAINT email_delivery_attempt_number_valid CHECK (attempt_number > 0),
    CONSTRAINT email_delivery_attempt_status_known CHECK (status IN ('STARTED', 'SENT', 'FAILED')),
    CONSTRAINT email_delivery_attempt_result_consistent CHECK (
        (status = 'STARTED' AND completed_at IS NULL
            AND provider_message_id IS NULL AND failure_code IS NULL AND failure_message IS NULL)
        OR (status = 'SENT' AND completed_at IS NOT NULL
            AND provider_message_id IS NOT NULL AND failure_code IS NULL AND failure_message IS NULL)
        OR (status = 'FAILED' AND completed_at IS NOT NULL
            AND provider_message_id IS NULL AND failure_code IS NOT NULL AND failure_message IS NOT NULL)
    )
);

CREATE INDEX email_delivery_attempt_notification_idx
    ON email_delivery_attempt(notification_id, attempt_number DESC);
