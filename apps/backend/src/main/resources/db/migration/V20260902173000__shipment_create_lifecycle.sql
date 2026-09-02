ALTER TABLE transport_capacity_match_search
    DROP CONSTRAINT transport_match_status_known;

ALTER TABLE transport_capacity_match_search
    ADD CONSTRAINT transport_match_status_known CHECK (
        status IN ('MATCHED', 'NO_MATCH', 'RESERVED', 'ASSIGNED', 'RELEASED', 'EXPIRED')
    );

ALTER TABLE transport_capacity_reservation
    DROP CONSTRAINT transport_reservation_status_known;

ALTER TABLE transport_capacity_reservation
    DROP CONSTRAINT transport_reservation_times_consistent;

ALTER TABLE transport_capacity_reservation
    ADD CONSTRAINT transport_reservation_status_known CHECK (
        status IN ('ACTIVE', 'CONSUMED', 'RELEASED', 'EXPIRED')
    );

ALTER TABLE transport_capacity_reservation
    ADD CONSTRAINT transport_reservation_times_consistent CHECK (
        expires_at > created_at
        AND ((status IN ('ACTIVE', 'CONSUMED') AND released_at IS NULL)
            OR (status IN ('RELEASED', 'EXPIRED') AND released_at IS NOT NULL))
    );

CREATE TABLE shipment_record (
    id UUID PRIMARY KEY,
    requested_by_business_id UUID NOT NULL REFERENCES business_profile(id),
    client_request_id UUID NOT NULL,
    input_fingerprint CHAR(64) NOT NULL,
    demand_group_suggestion_id UUID NOT NULL,
    capacity_search_id UUID NOT NULL REFERENCES transport_capacity_match_search(id),
    capacity_reservation_id UUID NOT NULL REFERENCES transport_capacity_reservation(id),
    capacity_offer_id UUID NOT NULL REFERENCES transport_capacity_offer(id),
    transporter_id UUID NOT NULL REFERENCES transport_transporter(id),
    reserved_weight_kg NUMERIC(15, 3) NOT NULL,
    reserved_volume_cubic_metres NUMERIC(15, 3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT shipment_request_unique UNIQUE (requested_by_business_id, client_request_id),
    CONSTRAINT shipment_input_fingerprint_shape CHECK (input_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT shipment_capacity_positive CHECK (
        reserved_weight_kg > 0 AND reserved_volume_cubic_metres > 0
    ),
    CONSTRAINT shipment_status_known CHECK (status IN (
        'AWAITING_COLLECTION', 'COLLECTED', 'IN_TRANSIT', 'DELAYED',
        'DELIVERED', 'DISPUTED', 'CANCELLED'
    )),
    CONSTRAINT shipment_time_order CHECK (updated_at >= created_at)
);

CREATE INDEX shipment_business_status_idx
    ON shipment_record(requested_by_business_id, status, created_at DESC);

CREATE TABLE shipment_load_order (
    shipment_id UUID NOT NULL REFERENCES shipment_record(id),
    sequence INTEGER NOT NULL,
    order_id UUID NOT NULL,
    buyer_business_id UUID NOT NULL,
    destination_label VARCHAR(500) NOT NULL,
    destination_latitude DOUBLE PRECISION NOT NULL,
    destination_longitude DOUBLE PRECISION NOT NULL,
    delivery_window_start TIMESTAMPTZ NOT NULL,
    delivery_window_end TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (shipment_id, sequence),
    CONSTRAINT shipment_load_order_unique UNIQUE (shipment_id, order_id),
    CONSTRAINT shipment_load_order_sequence_valid CHECK (sequence >= 0),
    CONSTRAINT shipment_load_order_coordinates_valid CHECK (
        destination_latitude BETWEEN -90 AND 90
        AND destination_longitude BETWEEN -180 AND 180
    ),
    CONSTRAINT shipment_load_order_window_valid CHECK (delivery_window_end > delivery_window_start)
);

CREATE TABLE shipment_load_cargo_item (
    shipment_id UUID NOT NULL,
    order_sequence INTEGER NOT NULL,
    sequence INTEGER NOT NULL,
    product_code VARCHAR(100) NOT NULL,
    unit_of_measure VARCHAR(50) NOT NULL,
    PRIMARY KEY (shipment_id, order_sequence, sequence),
    CONSTRAINT shipment_load_cargo_order_fk
        FOREIGN KEY (shipment_id, order_sequence)
        REFERENCES shipment_load_order(shipment_id, sequence),
    CONSTRAINT shipment_load_cargo_sequence_valid CHECK (sequence >= 0)
);

CREATE TABLE shipment_assignment (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipment_record(id),
    command_id UUID NOT NULL,
    input_fingerprint CHAR(64) NOT NULL,
    sequence INTEGER NOT NULL,
    transporter_id UUID NOT NULL REFERENCES transport_transporter(id),
    transport_assignment_id UUID NOT NULL REFERENCES transport_driver_vehicle_assignment(id),
    vehicle_id UUID NOT NULL REFERENCES transport_vehicle(id),
    vehicle_registration_number VARCHAR(32) NOT NULL,
    vehicle_description VARCHAR(255) NOT NULL,
    driver_id UUID NOT NULL REFERENCES transport_driver(id),
    driver_display_name VARCHAR(255) NOT NULL,
    driver_reference VARCHAR(100) NOT NULL,
    route_assessment_id UUID NOT NULL REFERENCES routing_assessment(id),
    route_calculation_id UUID NOT NULL REFERENCES routing_calculation(id),
    route_candidate_id UUID NOT NULL REFERENCES routing_candidate(id),
    cargo_profile VARCHAR(64) NOT NULL,
    route_algorithm_version VARCHAR(64) NOT NULL,
    route_score NUMERIC(8, 6) NOT NULL,
    route_confidence NUMERIC(8, 6) NOT NULL,
    route_geometry geometry(LineString, 4326) NOT NULL,
    route_distance_metres BIGINT NOT NULL,
    route_duration_seconds BIGINT NOT NULL,
    route_toll_estimate_zar NUMERIC(15, 2) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    reason VARCHAR(500) NOT NULL,
    correlation_id UUID NOT NULL,
    source VARCHAR(32) NOT NULL,
    actor_user_id UUID NOT NULL REFERENCES access_user_account(id),
    CONSTRAINT shipment_assignment_command_unique UNIQUE (shipment_id, command_id),
    CONSTRAINT shipment_assignment_sequence_unique UNIQUE (shipment_id, sequence),
    CONSTRAINT shipment_assignment_fingerprint_shape CHECK (input_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT shipment_assignment_route_score_valid CHECK (
        route_score BETWEEN 0 AND 1 AND route_confidence BETWEEN 0 AND 1
    ),
    CONSTRAINT shipment_assignment_route_values_valid CHECK (
        route_distance_metres > 0
        AND route_duration_seconds > 0
        AND route_toll_estimate_zar >= 0
        AND ST_IsValid(route_geometry)
        AND NOT ST_IsEmpty(route_geometry)
    ),
    CONSTRAINT shipment_assignment_time_order CHECK (ended_at IS NULL OR ended_at >= started_at),
    CONSTRAINT shipment_assignment_source_known CHECK (
        source IN ('API', 'OPERATIONS', 'DRIVER_APP', 'HANDOVER', 'TELEMETRY', 'SYSTEM')
    ),
    CONSTRAINT shipment_assignment_scored_route_fk
        FOREIGN KEY (route_assessment_id, route_candidate_id)
        REFERENCES routing_candidate_score(assessment_id, candidate_id)
);

CREATE UNIQUE INDEX shipment_one_active_assignment_idx
    ON shipment_assignment(shipment_id)
    WHERE ended_at IS NULL;

CREATE INDEX shipment_assignment_route_geometry_gix
    ON shipment_assignment USING GIST(route_geometry);

CREATE TABLE shipment_transition (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES shipment_record(id),
    command_id UUID NOT NULL,
    input_fingerprint CHAR(64) NOT NULL,
    sequence INTEGER NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    actor_user_id UUID NOT NULL REFERENCES access_user_account(id),
    occurred_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(500) NOT NULL,
    correlation_id UUID NOT NULL,
    source VARCHAR(32) NOT NULL,
    CONSTRAINT shipment_transition_command_unique UNIQUE (shipment_id, command_id),
    CONSTRAINT shipment_transition_sequence_unique UNIQUE (shipment_id, sequence),
    CONSTRAINT shipment_transition_fingerprint_shape CHECK (input_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT shipment_transition_from_status_known CHECK (
        from_status IS NULL OR from_status IN (
            'AWAITING_COLLECTION', 'COLLECTED', 'IN_TRANSIT', 'DELAYED',
            'DELIVERED', 'DISPUTED', 'CANCELLED'
        )
    ),
    CONSTRAINT shipment_transition_to_status_known CHECK (to_status IN (
        'AWAITING_COLLECTION', 'COLLECTED', 'IN_TRANSIT', 'DELAYED',
        'DELIVERED', 'DISPUTED', 'CANCELLED'
    )),
    CONSTRAINT shipment_transition_initial_shape CHECK (
        from_status IS NOT NULL OR (sequence = 0 AND to_status = 'AWAITING_COLLECTION')
    ),
    CONSTRAINT shipment_transition_source_known CHECK (
        source IN ('API', 'OPERATIONS', 'DRIVER_APP', 'HANDOVER', 'TELEMETRY', 'SYSTEM')
    )
);

CREATE UNIQUE INDEX shipment_one_initial_transition_idx
    ON shipment_transition(shipment_id)
    WHERE from_status IS NULL;
