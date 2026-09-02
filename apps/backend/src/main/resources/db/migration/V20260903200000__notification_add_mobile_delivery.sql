-- Version follows the independently merged trust-score migration.
ALTER TABLE notification_preference
    ADD COLUMN sms_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN whatsapp_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE notification_contact_point (
    user_id UUID PRIMARY KEY REFERENCES access_user_account(id) ON DELETE CASCADE,
    protected_phone VARCHAR(4000) NOT NULL,
    phone_fingerprint CHAR(64) NOT NULL,
    phone_last_four CHAR(4) NOT NULL,
    sms_consented_at TIMESTAMPTZ,
    whatsapp_consented_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT notification_contact_phone_fingerprint_shape CHECK (
        phone_fingerprint ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT notification_contact_phone_suffix_shape CHECK (
        phone_last_four ~ '^[0-9]{4}$'
    ),
    CONSTRAINT notification_contact_times_consistent CHECK (
        updated_at >= created_at
        AND (sms_consented_at IS NULL OR sms_consented_at >= created_at)
        AND (whatsapp_consented_at IS NULL OR whatsapp_consented_at >= created_at)
    )
);

CREATE TABLE mobile_notification (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(200) NOT NULL UNIQUE,
    request_fingerprint CHAR(64) NOT NULL,
    recipient_user_id UUID REFERENCES access_user_account(id) ON DELETE SET NULL,
    channel VARCHAR(16) NOT NULL,
    category VARCHAR(32) NOT NULL,
    template_key VARCHAR(100) NOT NULL,
    template_version INTEGER NOT NULL,
    protected_recipient VARCHAR(4000),
    recipient_last_four CHAR(4),
    status VARCHAR(32) NOT NULL,
    provider_key VARCHAR(100),
    provider_message_id VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT mobile_notification_fingerprint_shape CHECK (
        request_fingerprint ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT mobile_notification_recipient_suffix_shape CHECK (
        recipient_last_four IS NULL OR recipient_last_four ~ '^[0-9]{4}$'
    ),
    CONSTRAINT mobile_notification_channel_known CHECK (
        channel IN ('SMS', 'WHATSAPP')
    ),
    CONSTRAINT mobile_notification_category_known CHECK (category IN (
        'SUPPLIER_INVITATION',
        'PROCUREMENT_UPDATE',
        'SHIPMENT_UPDATE',
        'SECURITY_ALERT'
    )),
    CONSTRAINT mobile_notification_template_version_valid CHECK (template_version > 0),
    CONSTRAINT mobile_notification_status_known CHECK (status IN (
        'PENDING',
        'SUBMITTING',
        'SUBMISSION_UNKNOWN',
        'ACCEPTED',
        'QUEUED',
        'SENT',
        'DELIVERED',
        'READ',
        'FAILED',
        'REJECTED',
        'EXPIRED',
        'SUPPRESSED'
    )),
    CONSTRAINT mobile_notification_provider_identity_consistent CHECK (
        (provider_message_id IS NULL)
        OR (provider_key IS NOT NULL AND submitted_at IS NOT NULL)
    ),
    CONSTRAINT mobile_notification_recipient_consistent CHECK (
        (status = 'SUPPRESSED' AND protected_recipient IS NULL AND recipient_last_four IS NULL)
        OR (status <> 'SUPPRESSED' AND protected_recipient IS NOT NULL AND recipient_last_four IS NOT NULL)
    ),
    CONSTRAINT mobile_notification_times_consistent CHECK (
        updated_at >= created_at
        AND (submitted_at IS NULL OR submitted_at >= created_at)
        AND (sent_at IS NULL OR sent_at >= created_at)
        AND (delivered_at IS NULL OR delivered_at >= created_at)
        AND (read_at IS NULL OR read_at >= created_at)
        AND (failed_at IS NULL OR failed_at >= created_at)
        AND (read_at IS NULL OR delivered_at IS NOT NULL)
    ),
    CONSTRAINT mobile_notification_terminal_times_consistent CHECK (
        (status = 'DELIVERED' AND delivered_at IS NOT NULL AND failed_at IS NULL)
        OR (status = 'READ' AND read_at IS NOT NULL AND delivered_at IS NOT NULL AND failed_at IS NULL)
        OR (status IN ('FAILED', 'REJECTED', 'EXPIRED') AND failed_at IS NOT NULL)
        OR (status IN (
            'PENDING', 'SUBMITTING', 'SUBMISSION_UNKNOWN', 'ACCEPTED', 'QUEUED', 'SENT', 'SUPPRESSED'
        ) AND failed_at IS NULL)
    ),
    CONSTRAINT mobile_notification_provider_message_unique UNIQUE (provider_key, provider_message_id)
);

CREATE INDEX mobile_notification_recipient_status_idx
    ON mobile_notification(recipient_user_id, status, created_at DESC);

CREATE INDEX mobile_notification_reconciliation_idx
    ON mobile_notification(status, updated_at)
    WHERE status = 'SUBMISSION_UNKNOWN';

CREATE TABLE mobile_notification_template_data (
    notification_id UUID NOT NULL REFERENCES mobile_notification(id) ON DELETE CASCADE,
    data_key VARCHAR(100) NOT NULL,
    protected_data_value VARCHAR(4000) NOT NULL,
    PRIMARY KEY (notification_id, data_key)
);

CREATE TABLE mobile_delivery_attempt (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL REFERENCES mobile_notification(id) ON DELETE CASCADE,
    outbox_message_id UUID NOT NULL REFERENCES outbox_message(id),
    attempt_number INTEGER NOT NULL,
    provider_key VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL,
    provider_message_id VARCHAR(200),
    failure_code VARCHAR(64),
    failure_message VARCHAR(500),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT mobile_delivery_attempt_unique UNIQUE (
        notification_id, outbox_message_id, attempt_number
    ),
    CONSTRAINT mobile_delivery_attempt_number_valid CHECK (attempt_number > 0),
    CONSTRAINT mobile_delivery_attempt_status_known CHECK (
        status IN ('STARTED', 'ACCEPTED', 'FAILED', 'UNKNOWN')
    ),
    CONSTRAINT mobile_delivery_attempt_result_consistent CHECK (
        (status = 'STARTED' AND completed_at IS NULL
            AND provider_message_id IS NULL AND failure_code IS NULL AND failure_message IS NULL)
        OR (status = 'ACCEPTED' AND completed_at IS NOT NULL
            AND provider_message_id IS NOT NULL AND failure_code IS NULL AND failure_message IS NULL)
        OR (status = 'FAILED' AND completed_at IS NOT NULL
            AND provider_message_id IS NULL AND failure_code IS NOT NULL AND failure_message IS NOT NULL)
        OR (status = 'UNKNOWN' AND completed_at IS NOT NULL
            AND provider_message_id IS NULL AND failure_code IS NOT NULL AND failure_message IS NOT NULL)
    )
);

CREATE INDEX mobile_delivery_attempt_notification_idx
    ON mobile_delivery_attempt(notification_id, attempt_number DESC);

CREATE TABLE mobile_status_observation (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL REFERENCES mobile_notification(id) ON DELETE CASCADE,
    callback_fingerprint CHAR(64) NOT NULL UNIQUE,
    provider_key VARCHAR(100) NOT NULL,
    provider_message_id VARCHAR(200),
    provider_status VARCHAR(100) NOT NULL,
    observed_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT mobile_status_observation_fingerprint_shape CHECK (
        callback_fingerprint ~ '^[0-9a-f]{64}$'
    )
);

CREATE INDEX mobile_status_observation_notification_idx
    ON mobile_status_observation(notification_id, received_at DESC);
