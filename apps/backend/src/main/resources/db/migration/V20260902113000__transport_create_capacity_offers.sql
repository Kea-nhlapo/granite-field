CREATE TABLE transport_transporter (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL UNIQUE REFERENCES business_profile(id),
    display_name VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT transport_transporter_status_known CHECK (status IN ('ACTIVE'))
);

CREATE TABLE transport_vehicle (
    id UUID PRIMARY KEY,
    transporter_id UUID NOT NULL REFERENCES transport_transporter(id),
    client_request_id UUID NOT NULL,
    registration_number VARCHAR(32) NOT NULL,
    description VARCHAR(255) NOT NULL,
    maximum_weight_kg NUMERIC(15, 3) NOT NULL,
    maximum_volume_cubic_metres NUMERIC(15, 3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT transport_vehicle_request_unique UNIQUE (transporter_id, client_request_id),
    CONSTRAINT transport_vehicle_registration_unique UNIQUE (transporter_id, registration_number),
    CONSTRAINT transport_vehicle_owner_reference UNIQUE (id, transporter_id),
    CONSTRAINT transport_vehicle_status_known CHECK (status IN ('ACTIVE')),
    CONSTRAINT transport_vehicle_capacity_positive CHECK (
        maximum_weight_kg > 0 AND maximum_volume_cubic_metres > 0
    )
);

CREATE TABLE transport_driver (
    id UUID PRIMARY KEY,
    transporter_id UUID NOT NULL REFERENCES transport_transporter(id),
    client_request_id UUID NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    driver_reference VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT transport_driver_request_unique UNIQUE (transporter_id, client_request_id),
    CONSTRAINT transport_driver_reference_unique UNIQUE (transporter_id, driver_reference),
    CONSTRAINT transport_driver_owner_reference UNIQUE (id, transporter_id),
    CONSTRAINT transport_driver_status_known CHECK (status IN ('ACTIVE'))
);

CREATE TABLE transport_driver_vehicle_assignment (
    id UUID PRIMARY KEY,
    transporter_id UUID NOT NULL REFERENCES transport_transporter(id),
    client_request_id UUID NOT NULL,
    vehicle_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    assigned_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    ended_by_user_id UUID REFERENCES access_user_account(id),
    CONSTRAINT transport_assignment_request_unique UNIQUE (transporter_id, client_request_id),
    CONSTRAINT transport_assignment_offer_reference UNIQUE (id, vehicle_id, transporter_id),
    CONSTRAINT transport_assignment_vehicle_owner_fk
        FOREIGN KEY (vehicle_id, transporter_id) REFERENCES transport_vehicle(id, transporter_id),
    CONSTRAINT transport_assignment_driver_owner_fk
        FOREIGN KEY (driver_id, transporter_id) REFERENCES transport_driver(id, transporter_id),
    CONSTRAINT transport_assignment_end_consistent CHECK (
        (ended_at IS NULL AND ended_by_user_id IS NULL)
        OR (ended_at IS NOT NULL AND ended_by_user_id IS NOT NULL AND ended_at >= started_at)
    )
);

CREATE UNIQUE INDEX transport_assignment_one_active_vehicle_idx
    ON transport_driver_vehicle_assignment(vehicle_id)
    WHERE ended_at IS NULL;

CREATE UNIQUE INDEX transport_assignment_one_active_driver_idx
    ON transport_driver_vehicle_assignment(driver_id)
    WHERE ended_at IS NULL;

CREATE TABLE transport_capacity_offer (
    id UUID PRIMARY KEY,
    transporter_id UUID NOT NULL REFERENCES transport_transporter(id),
    client_request_id UUID NOT NULL,
    vehicle_id UUID NOT NULL,
    driver_assignment_id UUID NOT NULL,
    route_corridor GEOGRAPHY(LINESTRING, 4326) NOT NULL,
    corridor_radius_metres INTEGER NOT NULL,
    departure_window_start TIMESTAMPTZ NOT NULL,
    departure_window_end TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    total_weight_kg NUMERIC(15, 3) NOT NULL,
    remaining_weight_kg NUMERIC(15, 3) NOT NULL,
    total_volume_cubic_metres NUMERIC(15, 3) NOT NULL,
    remaining_volume_cubic_metres NUMERIC(15, 3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    created_at TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ,
    CONSTRAINT transport_offer_request_unique UNIQUE (transporter_id, client_request_id),
    CONSTRAINT transport_offer_vehicle_owner_fk
        FOREIGN KEY (vehicle_id, transporter_id) REFERENCES transport_vehicle(id, transporter_id),
    CONSTRAINT transport_offer_assignment_fk
        FOREIGN KEY (driver_assignment_id, vehicle_id, transporter_id)
        REFERENCES transport_driver_vehicle_assignment(id, vehicle_id, transporter_id),
    CONSTRAINT transport_offer_corridor_radius_valid CHECK (
        corridor_radius_metres BETWEEN 1 AND 250000
    ),
    CONSTRAINT transport_offer_window_valid CHECK (
        departure_window_end > departure_window_start
    ),
    CONSTRAINT transport_offer_expiry_valid CHECK (
        expires_at > created_at AND expires_at <= departure_window_end
    ),
    CONSTRAINT transport_offer_capacity_positive CHECK (
        total_weight_kg > 0 AND total_volume_cubic_metres > 0
    ),
    CONSTRAINT transport_offer_capacity_remaining_valid CHECK (
        remaining_weight_kg >= 0
        AND remaining_weight_kg <= total_weight_kg
        AND remaining_volume_cubic_metres >= 0
        AND remaining_volume_cubic_metres <= total_volume_cubic_metres
    ),
    CONSTRAINT transport_offer_status_known CHECK (status IN ('ACTIVE', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT transport_offer_cancel_consistent CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
        OR (status IN ('ACTIVE', 'EXPIRED') AND cancelled_at IS NULL)
    )
);

CREATE INDEX transport_capacity_offer_corridor_idx
    ON transport_capacity_offer USING GIST(route_corridor);

CREATE INDEX transport_capacity_offer_available_idx
    ON transport_capacity_offer(status, departure_window_start, expires_at)
    WHERE status = 'ACTIVE';

CREATE TABLE transport_capacity_offer_route_point (
    offer_id UUID NOT NULL REFERENCES transport_capacity_offer(id),
    point_sequence INTEGER NOT NULL,
    label VARCHAR(255),
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    PRIMARY KEY (offer_id, point_sequence),
    CONSTRAINT transport_route_point_sequence_valid CHECK (point_sequence >= 0)
);

CREATE TABLE transport_capacity_offer_restriction (
    offer_id UUID NOT NULL REFERENCES transport_capacity_offer(id),
    restriction VARCHAR(64) NOT NULL,
    PRIMARY KEY (offer_id, restriction),
    CONSTRAINT transport_offer_restriction_known CHECK (restriction IN (
        'DRY_GOODS_ONLY',
        'FOOD_GRADE_ONLY',
        'NO_HAZARDOUS_GOODS',
        'NO_HIGH_VALUE_CARGO',
        'NO_TEMPERATURE_CONTROLLED_CARGO'
    ))
);
