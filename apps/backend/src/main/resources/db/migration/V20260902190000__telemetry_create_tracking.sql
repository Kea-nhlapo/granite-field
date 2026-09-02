CREATE TABLE telemetry_device (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL REFERENCES business_profile(id),
    shipment_id UUID NOT NULL REFERENCES shipment_record(id),
    display_name VARCHAR(120) NOT NULL,
    credential_hash CHAR(64) NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    created_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT telemetry_device_credential_shape CHECK (credential_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT telemetry_device_status_known CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT telemetry_device_time_order CHECK (
        (last_seen_at IS NULL OR last_seen_at >= created_at)
        AND ((status = 'ACTIVE' AND revoked_at IS NULL)
            OR (status = 'REVOKED' AND revoked_at IS NOT NULL AND revoked_at >= created_at))
    )
);

CREATE INDEX telemetry_device_shipment_idx
    ON telemetry_device(business_id, shipment_id, status);

CREATE TABLE telemetry_reading (
    id UUID PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES telemetry_device(id),
    shipment_id UUID NOT NULL REFERENCES shipment_record(id),
    client_event_id UUID NOT NULL,
    input_fingerprint CHAR(64) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    position geometry(Point, 4326),
    speed_kph NUMERIC(8, 3),
    fuel_litres NUMERIC(12, 3),
    temperature_celsius NUMERIC(7, 3),
    seal_open BOOLEAN,
    battery_percent NUMERIC(6, 3),
    network_status VARCHAR(16),
    network_signal_dbm INTEGER,
    retention_tier VARCHAR(16) NOT NULL DEFAULT 'RAW',
    CONSTRAINT telemetry_reading_event_unique UNIQUE (device_id, client_event_id),
    CONSTRAINT telemetry_reading_fingerprint_shape CHECK (input_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT telemetry_reading_has_value CHECK (
        position IS NOT NULL OR speed_kph IS NOT NULL OR fuel_litres IS NOT NULL
        OR temperature_celsius IS NOT NULL OR seal_open IS NOT NULL
        OR battery_percent IS NOT NULL OR network_status IS NOT NULL
        OR network_signal_dbm IS NOT NULL
    ),
    CONSTRAINT telemetry_reading_position_valid CHECK (
        position IS NULL OR (
            ST_SRID(position) = 4326
            AND NOT ST_IsEmpty(position)
            AND ST_Y(position) BETWEEN -90 AND 90
            AND ST_X(position) BETWEEN -180 AND 180
        )
    ),
    CONSTRAINT telemetry_reading_values_valid CHECK (
        (speed_kph IS NULL OR speed_kph BETWEEN 0 AND 350)
        AND (fuel_litres IS NULL OR fuel_litres BETWEEN 0 AND 10000)
        AND (temperature_celsius IS NULL OR temperature_celsius BETWEEN -100 AND 150)
        AND (battery_percent IS NULL OR battery_percent BETWEEN 0 AND 100)
        AND (network_signal_dbm IS NULL OR network_signal_dbm BETWEEN -200 AND 0)
    ),
    CONSTRAINT telemetry_reading_network_known CHECK (
        network_status IS NULL OR network_status IN ('CONNECTED', 'LIMITED', 'OFFLINE', 'UNKNOWN')
    ),
    CONSTRAINT telemetry_reading_retention_tier_known CHECK (
        retention_tier IN ('RAW', 'DOWNSAMPLED')
    )
);

CREATE INDEX telemetry_reading_shipment_time_idx
    ON telemetry_reading(shipment_id, recorded_at DESC, received_at DESC);

CREATE INDEX telemetry_reading_device_received_idx
    ON telemetry_reading(device_id, received_at DESC);

CREATE INDEX telemetry_reading_position_gix
    ON telemetry_reading USING GIST(position)
    WHERE position IS NOT NULL;

CREATE TABLE telemetry_live_position (
    shipment_id UUID PRIMARY KEY REFERENCES shipment_record(id),
    device_id UUID NOT NULL REFERENCES telemetry_device(id),
    reading_id UUID NOT NULL UNIQUE,
    recorded_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    position geometry(Point, 4326) NOT NULL,
    speed_kph NUMERIC(8, 3),
    battery_percent NUMERIC(6, 3),
    network_status VARCHAR(16),
    network_signal_dbm INTEGER,
    CONSTRAINT telemetry_live_position_valid CHECK (
        ST_SRID(position) = 4326
        AND NOT ST_IsEmpty(position)
        AND ST_Y(position) BETWEEN -90 AND 90
        AND ST_X(position) BETWEEN -180 AND 180
    ),
    CONSTRAINT telemetry_live_values_valid CHECK (
        (speed_kph IS NULL OR speed_kph BETWEEN 0 AND 350)
        AND (battery_percent IS NULL OR battery_percent BETWEEN 0 AND 100)
        AND (network_signal_dbm IS NULL OR network_signal_dbm BETWEEN -200 AND 0)
    ),
    CONSTRAINT telemetry_live_network_known CHECK (
        network_status IS NULL OR network_status IN ('CONNECTED', 'LIMITED', 'OFFLINE', 'UNKNOWN')
    )
);

CREATE INDEX telemetry_live_position_gix
    ON telemetry_live_position USING GIST(position);
