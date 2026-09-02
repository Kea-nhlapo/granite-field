package za.co.trademesh.shared.events.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import za.co.trademesh.shared.events.EventEnvelope;

/**
 * SQL for the outbox. Every statement is parameterised; no value is ever
 * concatenated into a statement.
 */
@Repository
public class OutboxRepository {

    private final ObjectProvider<JdbcClient> jdbcProvider;

    /**
     * The client is resolved per call rather than injected directly.
     *
     * <p>{@code JdbcClient} comes from autoconfiguration, so a context started
     * without a datasource — which DatasourceCredentialDefaultsTest does
     * deliberately, to assert property resolution without touching a database —
     * has no such bean, and a constructor dependency would fail that context at
     * startup. Deferring means the outbox costs nothing to a context that never
     * uses it, and still fails loudly and immediately on first use if a real
     * deployment is somehow missing its datasource.
     *
     * <p>{@code @ConditionalOnBean} is not the tool here: user configuration is
     * processed before autoconfiguration, so the condition would find no
     * DataSource yet and skip the outbox in production too.
     */
    public OutboxRepository(ObjectProvider<JdbcClient> jdbcProvider) {
        this.jdbcProvider = jdbcProvider;
    }

    private JdbcClient jdbc() {
        return jdbcProvider.getObject();
    }

    /**
     * Enqueues a message, or does nothing if this (type, idempotency key) is
     * already queued.
     *
     * <p>ON CONFLICT DO NOTHING is not a convenience. This runs inside the
     * caller's business transaction, and in PostgreSQL a unique-violation
     * aborts the entire transaction — every later statement fails with
     * "current transaction is aborted". A plain INSERT would mean that the
     * duplicate enqueue idempotency exists to tolerate is precisely what
     * destroys the caller's write.
     *
     * <p>The payload is cast explicitly with {@code ?::jsonb}. PostgreSQL will
     * not implicitly cast a bound text parameter to jsonb, so without the cast
     * this fails at runtime with a type error, not at compile time.
     *
     * @return true if a row was written, false if it was already queued
     */
    public boolean enqueue(
            UUID id, String type, String payload, String idempotencyKey, EventEnvelope envelope, Instant availableAt) {

        int written = jdbc().sql("""
                INSERT INTO outbox_message (
                    id, type, payload, idempotency_key, status, attempts,
                    available_at, correlation_id, actor, source, schema_version)
                VALUES (?, ?, ?::jsonb, ?, 'PENDING', 0, ?, ?, ?, ?, ?)
                ON CONFLICT (type, idempotency_key) DO NOTHING
                """)
                .param(id)
                .param(type)
                .param(payload)
                .param(idempotencyKey)
                .param(Timestamp.from(availableAt))
                .param(envelope.correlationId())
                .param(envelope.actor().orElse(null))
                .param(envelope.source())
                .param(envelope.schemaVersion())
                .update();

        return written == 1;
    }

    /**
     * Takes ownership of up to {@code batchSize} due messages and returns them.
     *
     * <p>Claiming is an UPDATE, not a held row lock. FOR UPDATE SKIP LOCKED
     * inside the subquery stops two concurrent claims from selecting the same
     * rows; the UPDATE then records the ownership durably, so the transaction
     * can commit immediately and the handler can run without a database
     * transaction open around it.
     *
     * <p>Run this in its own short transaction.
     */
    public List<OutboxMessage> claimBatch(int batchSize, Instant now) {
        UUID claimToken = UUID.randomUUID();
        return jdbc().sql("""
                UPDATE outbox_message
                   SET status = 'CLAIMED',
                       claimed_at = ?,
                       claim_token = ?,
                       attempts = attempts + 1,
                       updated_at = ?
                 WHERE id IN (
                       SELECT id
                         FROM outbox_message
                        WHERE status = 'PENDING'
                          AND available_at <= ?
                        ORDER BY available_at
                        LIMIT ?
                          FOR UPDATE SKIP LOCKED)
             RETURNING id, type, payload, idempotency_key, attempts, available_at,
                       created_at, correlation_id, actor, source, schema_version, claim_token
                """)
                .param(Timestamp.from(now))
                .param(claimToken)
                .param(Timestamp.from(now))
                .param(Timestamp.from(now))
                .param(batchSize)
                .query(OutboxRepository::toMessage)
                .list();
    }

    /**
     * Marks a claimed message finished.
     *
     * <p>Guarded on status = 'CLAIMED' so a worker whose claim the reaper has
     * already revoked cannot mark DONE a message another worker now owns.
     */
    public boolean markDone(UUID id, UUID claimToken) {
        return jdbc().sql("""
                UPDATE outbox_message
                   SET status = 'DONE', claimed_at = NULL, claim_token = NULL,
                       last_error = NULL, updated_at = now()
                 WHERE id = ? AND status = 'CLAIMED' AND claim_token = ?
                """).param(id).param(claimToken).update() == 1;
    }

    /** Returns a failed message to PENDING with a later availability. */
    public boolean markForRetry(UUID id, UUID claimToken, Instant availableAt, String error) {
        return jdbc().sql("""
                UPDATE outbox_message
                   SET status = 'PENDING', claimed_at = NULL, claim_token = NULL, available_at = ?,
                       last_error = ?, updated_at = now()
                 WHERE id = ? AND status = 'CLAIMED' AND claim_token = ?
                """)
                        .param(Timestamp.from(availableAt))
                        .param(error)
                        .param(id)
                        .param(claimToken)
                        .update()
                == 1;
    }

    /** Retires a message that has exhausted its attempts. The row is kept. */
    public boolean markDead(UUID id, UUID claimToken, String error) {
        return jdbc().sql("""
                UPDATE outbox_message
                   SET status = 'DEAD', claimed_at = NULL, claim_token = NULL,
                       last_error = ?, updated_at = now()
                 WHERE id = ? AND status = 'CLAIMED' AND claim_token = ?
                """).param(error).param(id).param(claimToken).update() == 1;
    }

    /**
     * Returns messages whose claim has outlived the visibility timeout.
     *
     * <p>This is what makes a killed worker recoverable. Without it, a pod
     * terminated mid-dispatch leaves its messages CLAIMED forever, and the
     * queue drains to a permanent standstill that no error is ever logged for.
     *
     * <p>attempts is not incremented here; the claim that died already counted.
     */
    public int reapExpiredClaims(Instant now, Duration visibilityTimeout) {
        return jdbc().sql("""
                UPDATE outbox_message
                   SET status = 'PENDING', claimed_at = NULL, claim_token = NULL,
                       last_error = 'claim expired; worker presumed dead',
                       updated_at = ?
                 WHERE status = 'CLAIMED' AND claimed_at < ?
                """)
                .param(Timestamp.from(now))
                .param(Timestamp.from(now.minus(visibilityTimeout)))
                .update();
    }

    public Optional<OutboxRow> findById(UUID id) {
        return jdbc().sql("""
                SELECT id, type, status, attempts, available_at, claimed_at, last_error
                  FROM outbox_message WHERE id = ?
                """)
                .param(id)
                .query((rs, rowNum) -> new OutboxRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("type"),
                        rs.getString("status"),
                        rs.getInt("attempts"),
                        rs.getTimestamp("available_at").toInstant(),
                        Optional.ofNullable(rs.getTimestamp("claimed_at")).map(Timestamp::toInstant),
                        Optional.ofNullable(rs.getString("last_error"))))
                .optional();
    }

    private static OutboxMessage toMessage(ResultSet rs, int rowNum) throws SQLException {
        // eventId is the row id: a message is one publication, and reusing the
        // id keeps a handler's logs joinable to the row an operator can query.
        // occurredAt is created_at, when the fact was recorded — NOT
        // available_at, which retries move forward and which would make an
        // event appear to have happened later each time it failed.
        EventEnvelope envelope = new EventEnvelope(
                rs.getObject("id", UUID.class),
                rs.getString("type"),
                rs.getTimestamp("created_at").toInstant(),
                Optional.ofNullable(rs.getString("actor")),
                rs.getString("source"),
                rs.getObject("correlation_id", UUID.class),
                rs.getInt("schema_version"));

        return new OutboxMessage(
                rs.getObject("id", UUID.class),
                rs.getString("type"),
                rs.getString("payload"),
                rs.getString("idempotency_key"),
                rs.getInt("attempts"),
                rs.getObject("claim_token", UUID.class),
                envelope,
                rs.getTimestamp("available_at").toInstant());
    }

    /** Read model for tests and operator queries. */
    public record OutboxRow(
            UUID id,
            String type,
            String status,
            int attempts,
            Instant availableAt,
            Optional<Instant> claimedAt,
            Optional<String> lastError) {}
}
