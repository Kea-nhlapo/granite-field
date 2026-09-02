CREATE TABLE procurement_request (
    id UUID PRIMARY KEY,
    buyer_business_id UUID NOT NULL REFERENCES business_profile(id),
    client_request_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    destination_label VARCHAR(500) NOT NULL,
    destination GEOGRAPHY(POINT, 4326) NOT NULL,
    delivery_window_start TIMESTAMPTZ NOT NULL,
    delivery_window_end TIMESTAMPTZ NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT procurement_request_client_unique UNIQUE (buyer_business_id, client_request_id),
    CONSTRAINT procurement_request_status_known CHECK (status IN ('OPEN', 'QUOTED', 'ORDERED', 'CANCELLED')),
    CONSTRAINT procurement_request_delivery_window_valid CHECK (delivery_window_end > delivery_window_start)
);

CREATE INDEX procurement_request_business_status_idx
    ON procurement_request(buyer_business_id, status, created_at DESC);

CREATE INDEX procurement_request_destination_idx
    ON procurement_request USING GIST(destination);

CREATE TABLE procurement_request_item (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES procurement_request(id),
    product_code VARCHAR(100),
    description VARCHAR(500) NOT NULL,
    quantity NUMERIC(19, 4) NOT NULL,
    unit_of_measure VARCHAR(16) NOT NULL,
    CONSTRAINT procurement_request_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT procurement_request_item_unit_known CHECK (
        unit_of_measure IN ('EACH', 'CASE', 'BOX', 'KG', 'LITRE', 'PALLET')
    )
);

CREATE INDEX procurement_request_item_request_idx
    ON procurement_request_item(request_id);

CREATE TABLE procurement_quote (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES procurement_request(id),
    buyer_business_id UUID NOT NULL REFERENCES business_profile(id),
    supplier_profile_id UUID NOT NULL REFERENCES supplier_profile(id),
    source_document_id UUID NOT NULL UNIQUE REFERENCES document_record(id),
    client_request_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    currency CHAR(3) NOT NULL,
    subtotal NUMERIC(19, 4) NOT NULL,
    tax_amount NUMERIC(19, 4) NOT NULL,
    total NUMERIC(19, 4) NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    created_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT procurement_quote_client_unique UNIQUE (request_id, client_request_id),
    CONSTRAINT procurement_quote_status_known CHECK (status IN ('ACTIVE', 'ACCEPTED', 'WITHDRAWN', 'EXPIRED')),
    CONSTRAINT procurement_quote_currency_shape CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT procurement_quote_amounts_non_negative CHECK (
        subtotal >= 0 AND tax_amount >= 0 AND total >= 0
    ),
    CONSTRAINT procurement_quote_total_consistent CHECK (total = subtotal + tax_amount)
);

CREATE INDEX procurement_quote_request_idx
    ON procurement_quote(request_id, status, created_at DESC);

CREATE TABLE procurement_quote_item (
    id UUID PRIMARY KEY,
    quote_id UUID NOT NULL REFERENCES procurement_quote(id),
    request_item_id UUID NOT NULL REFERENCES procurement_request_item(id),
    description VARCHAR(500) NOT NULL,
    quantity NUMERIC(19, 4) NOT NULL,
    unit_of_measure VARCHAR(16) NOT NULL,
    unit_price NUMERIC(19, 4) NOT NULL,
    line_total NUMERIC(19, 4) NOT NULL,
    CONSTRAINT procurement_quote_request_item_unique UNIQUE (quote_id, request_item_id),
    CONSTRAINT procurement_quote_item_values_valid CHECK (
        quantity > 0 AND unit_price >= 0 AND line_total >= 0
    ),
    CONSTRAINT procurement_quote_item_unit_known CHECK (
        unit_of_measure IN ('EACH', 'CASE', 'BOX', 'KG', 'LITRE', 'PALLET')
    )
);

CREATE TABLE procurement_order (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL UNIQUE REFERENCES procurement_request(id),
    source_quote_id UUID NOT NULL UNIQUE REFERENCES procurement_quote(id),
    buyer_business_id UUID NOT NULL REFERENCES business_profile(id),
    supplier_profile_id UUID NOT NULL REFERENCES supplier_profile(id),
    source_document_id UUID NOT NULL REFERENCES document_record(id),
    confirmation_request_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    currency CHAR(3) NOT NULL,
    subtotal NUMERIC(19, 4) NOT NULL,
    tax_amount NUMERIC(19, 4) NOT NULL,
    total NUMERIC(19, 4) NOT NULL,
    destination_label VARCHAR(500) NOT NULL,
    destination GEOGRAPHY(POINT, 4326) NOT NULL,
    delivery_window_start TIMESTAMPTZ NOT NULL,
    delivery_window_end TIMESTAMPTZ NOT NULL,
    confirmed_by_user_id UUID NOT NULL REFERENCES access_user_account(id),
    confirmed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT procurement_order_confirmation_unique UNIQUE (buyer_business_id, confirmation_request_id),
    CONSTRAINT procurement_order_status_known CHECK (status = 'CONFIRMED'),
    CONSTRAINT procurement_order_currency_shape CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT procurement_order_amounts_non_negative CHECK (
        subtotal >= 0 AND tax_amount >= 0 AND total >= 0
    ),
    CONSTRAINT procurement_order_total_consistent CHECK (total = subtotal + tax_amount),
    CONSTRAINT procurement_order_delivery_window_valid CHECK (delivery_window_end > delivery_window_start)
);

CREATE INDEX procurement_order_business_confirmed_idx
    ON procurement_order(buyer_business_id, confirmed_at DESC);

CREATE INDEX procurement_order_destination_idx
    ON procurement_order USING GIST(destination);

CREATE TABLE procurement_order_item (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES procurement_order(id),
    source_request_item_id UUID NOT NULL,
    product_code VARCHAR(100),
    description VARCHAR(500) NOT NULL,
    quantity NUMERIC(19, 4) NOT NULL,
    unit_of_measure VARCHAR(16) NOT NULL,
    unit_price NUMERIC(19, 4) NOT NULL,
    line_total NUMERIC(19, 4) NOT NULL,
    CONSTRAINT procurement_order_source_item_unique UNIQUE (order_id, source_request_item_id),
    CONSTRAINT procurement_order_item_values_valid CHECK (
        quantity > 0 AND unit_price >= 0 AND line_total >= 0
    ),
    CONSTRAINT procurement_order_item_unit_known CHECK (
        unit_of_measure IN ('EACH', 'CASE', 'BOX', 'KG', 'LITRE', 'PALLET')
    )
);
