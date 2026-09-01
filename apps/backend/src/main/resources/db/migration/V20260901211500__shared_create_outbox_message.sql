-- Outbox for work that can fail or take time (issue #4).
--
-- One table, no broker. Ownership of a message is expressed by the status
-- column and claimed_at, NOT by holding a row lock for the duration of the
-- handler: a lock lives as long as its transaction, and a transaction held
-- open across a slow handler pins a pooled connection idle-in-transaction.

CREATE TABLE outbox_message (
    id              uuid        PRIMARY KEY,
    type            text        NOT NULL,
    payload         jsonb       NOT NULL,

    -- Caller-supplied natural key. Scoped to the type: two handlers keyed on
    -- the same business id (a shipment uuid, say) are different messages and
    -- must not collide.
    idempotency_key text        NOT NULL,

    status          text        NOT NULL,
    attempts        integer     NOT NULL DEFAULT 0,

    -- Earliest time a worker may claim this message. Retries push it forward.
    available_at    timestamptz NOT NULL DEFAULT now(),

    -- Set when a worker takes the message, cleared when it finishes. A worker
    -- that dies mid-dispatch leaves this set; the reaper uses it to return the
    -- message to PENDING once the visibility timeout has passed.
    claimed_at      timestamptz,

    last_error      text,

    -- Envelope metadata (issue #4 acceptance criteria). actor is nullable
    -- because a message enqueued by a scheduled sweep has no human actor;
    -- a sentinel string would only drift between callers.
    correlation_id  uuid        NOT NULL,
    actor           text,
    source          text        NOT NULL,
    schema_version  integer     NOT NULL,

    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT outbox_message_status_known
        CHECK (status IN ('PENDING', 'CLAIMED', 'DONE', 'DEAD')),
    CONSTRAINT outbox_message_attempts_not_negative
        CHECK (attempts >= 0),
    CONSTRAINT outbox_message_idempotent_per_type
        UNIQUE (type, idempotency_key)
);

-- The claim query filters on status and available_at and orders by
-- available_at. DONE rows accumulate and would otherwise be scanned forever,
-- so the index is partial.
CREATE INDEX outbox_message_claimable
    ON outbox_message (available_at)
    WHERE status = 'PENDING';

-- Supports the reaper, which looks only at messages currently held.
CREATE INDEX outbox_message_claimed
    ON outbox_message (claimed_at)
    WHERE status = 'CLAIMED';
