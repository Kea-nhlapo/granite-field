CREATE TABLE routing_calculation (
    id UUID PRIMARY KEY,
    requested_by_business_id UUID NOT NULL REFERENCES business_profile(id),
    client_request_id UUID NOT NULL,
    recalculation_of_id UUID REFERENCES routing_calculation(id),
    input_fingerprint CHAR(64) NOT NULL,
    origin_label VARCHAR(255),
    origin_latitude DOUBLE PRECISION NOT NULL,
    origin_longitude DOUBLE PRECISION NOT NULL,
    destination_label VARCHAR(255),
    destination_latitude DOUBLE PRECISION NOT NULL,
    destination_longitude DOUBLE PRECISION NOT NULL,
    maximum_weight_kg NUMERIC(15, 3) NOT NULL,
    maximum_height_metres NUMERIC(8, 3) NOT NULL,
    maximum_width_metres NUMERIC(8, 3) NOT NULL,
    maximum_length_metres NUMERIC(8, 3) NOT NULL,
    provider_name VARCHAR(64) NOT NULL,
    provider_version VARCHAR(64) NOT NULL,
    fallback_used BOOLEAN NOT NULL,
    fallback_reason VARCHAR(64),
    created_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT routing_calculation_request_unique UNIQUE (requested_by_business_id, client_request_id),
    CONSTRAINT routing_calculation_fingerprint_shape CHECK (input_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT routing_calculation_coordinates_valid CHECK (
        origin_latitude BETWEEN -90 AND 90
        AND origin_longitude BETWEEN -180 AND 180
        AND destination_latitude BETWEEN -90 AND 90
        AND destination_longitude BETWEEN -180 AND 180
    ),
    CONSTRAINT routing_calculation_vehicle_limits_positive CHECK (
        maximum_weight_kg > 0
        AND maximum_height_metres > 0
        AND maximum_width_metres > 0
        AND maximum_length_metres > 0
    ),
    CONSTRAINT routing_calculation_fallback_reason_consistent CHECK (
        (fallback_used AND fallback_reason IS NOT NULL)
        OR (NOT fallback_used AND fallback_reason IS NULL)
    )
);

CREATE INDEX routing_calculation_recalculation_idx
    ON routing_calculation(recalculation_of_id)
    WHERE recalculation_of_id IS NOT NULL;

CREATE TABLE routing_waypoint (
    calculation_id UUID NOT NULL REFERENCES routing_calculation(id),
    sequence INTEGER NOT NULL,
    label VARCHAR(255),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (calculation_id, sequence),
    CONSTRAINT routing_waypoint_sequence_nonnegative CHECK (sequence >= 0),
    CONSTRAINT routing_waypoint_coordinates_valid CHECK (
        latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180
    )
);

CREATE TABLE routing_avoidance (
    calculation_id UUID NOT NULL REFERENCES routing_calculation(id),
    avoidance VARCHAR(32) NOT NULL,
    PRIMARY KEY (calculation_id, avoidance),
    CONSTRAINT routing_avoidance_known CHECK (
        avoidance IN ('FERRIES', 'HIGHWAYS', 'TOLLS', 'UNPAVED_ROADS')
    )
);

CREATE TABLE routing_candidate (
    id UUID PRIMARY KEY,
    calculation_id UUID NOT NULL REFERENCES routing_calculation(id),
    sequence INTEGER NOT NULL,
    provider_candidate_key VARCHAR(128) NOT NULL,
    label VARCHAR(100) NOT NULL,
    geometry geometry(LineString, 4326) NOT NULL,
    distance_metres BIGINT NOT NULL,
    duration_seconds BIGINT NOT NULL,
    toll_estimate_zar NUMERIC(15, 2) NOT NULL,
    CONSTRAINT routing_candidate_sequence_unique UNIQUE (calculation_id, sequence),
    CONSTRAINT routing_candidate_provider_key_unique UNIQUE (calculation_id, provider_candidate_key),
    CONSTRAINT routing_candidate_values_valid CHECK (
        sequence >= 0
        AND distance_metres > 0
        AND duration_seconds > 0
        AND toll_estimate_zar >= 0
        AND ST_IsValid(geometry)
        AND NOT ST_IsEmpty(geometry)
    )
);

CREATE INDEX routing_candidate_geometry_gix ON routing_candidate USING GIST (geometry);

CREATE TABLE routing_segment (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL REFERENCES routing_candidate(id),
    sequence INTEGER NOT NULL,
    from_label VARCHAR(255),
    to_label VARCHAR(255),
    geometry geometry(LineString, 4326) NOT NULL,
    distance_metres BIGINT NOT NULL,
    duration_seconds BIGINT NOT NULL,
    toll_estimate_zar NUMERIC(15, 2) NOT NULL,
    CONSTRAINT routing_segment_sequence_unique UNIQUE (candidate_id, sequence),
    CONSTRAINT routing_segment_values_valid CHECK (
        sequence >= 0
        AND distance_metres > 0
        AND duration_seconds > 0
        AND toll_estimate_zar >= 0
        AND ST_IsValid(geometry)
        AND NOT ST_IsEmpty(geometry)
    )
);

CREATE INDEX routing_segment_geometry_gix ON routing_segment USING GIST (geometry);
