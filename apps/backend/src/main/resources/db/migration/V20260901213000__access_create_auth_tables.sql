CREATE TABLE access_user_account (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE access_user_role (
    user_id UUID NOT NULL REFERENCES access_user_account(id) ON DELETE CASCADE,
    role VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT access_user_role_known CHECK (role IN (
        'BUSINESS_OWNER',
        'BUSINESS_MEMBER',
        'SUPPLIER',
        'TRANSPORTER',
        'DRIVER',
        'INTERNAL_RISK_ANALYST',
        'INSURER',
        'ADMINISTRATOR'
    ))
);

-- The Business module owns the business table and will add its foreign key in
-- that module's migration. Keeping the UUID here lets access control ship first
-- without pretending that this module owns the business lifecycle.
CREATE TABLE access_business_membership (
    business_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES access_user_account(id) ON DELETE CASCADE,
    membership_role VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (business_id, user_id),
    CONSTRAINT access_business_membership_known_role CHECK (
        membership_role IN ('BUSINESS_OWNER', 'BUSINESS_MEMBER')
    )
);

CREATE INDEX access_business_membership_user_idx
    ON access_business_membership(user_id, business_id);

CREATE TABLE access_refresh_session (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES access_user_account(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by_id UUID REFERENCES access_refresh_session(id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX access_refresh_session_active_idx
    ON access_refresh_session(token_hash, expires_at)
    WHERE revoked_at IS NULL;

CREATE INDEX access_refresh_session_user_idx
    ON access_refresh_session(user_id, created_at DESC);
