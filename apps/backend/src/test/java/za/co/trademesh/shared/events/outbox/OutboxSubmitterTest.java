package za.co.trademesh.shared.events.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.trademesh.shared.events.CorrelationContext;

class OutboxSubmitterTest extends OutboxTestSupport {

    @Autowired
    private OutboxSubmitter submitter;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void enqueuesAMessageWithItsPayloadAndEnvelope() {
        UUID correlationId = UUID.randomUUID();

        CorrelationContext.runWithin(
                correlationId,
                "user-7",
                () -> submitter.submit("notification.send", "invite-1", Map.of("to", "a@b.test"), 1));

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM outbox_message");

        assertThat(row.get("type")).isEqualTo("notification.send");
        assertThat(row.get("idempotency_key")).isEqualTo("invite-1");
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("attempts")).isEqualTo(0);
        assertThat(row.get("correlation_id")).isEqualTo(correlationId);
        assertThat(row.get("actor")).isEqualTo("user-7");
        assertThat(row.get("source")).isEqualTo("trademesh-backend");
        assertThat(row.get("schema_version")).isEqualTo(1);
        assertThat(row.get("payload").toString()).contains("a@b.test");
    }

    @Test
    void storesThePayloadAsJsonbRatherThanText() {
        submitter.submit("notification.send", "invite-1", Map.of("to", "a@b.test"), 1);

        // Reaching into the payload with -> only works if the column really is
        // jsonb. Without the ?::jsonb cast in the insert this fails at runtime,
        // and it would fail on the first real message rather than at build time.
        String recipient = jdbcTemplate.queryForObject("SELECT payload ->> 'to' FROM outbox_message", String.class);

        assertThat(recipient).isEqualTo("a@b.test");
    }

    @Test
    void treatsASecondEnqueueOfTheSameKeyAsANoOp() {
        assertThat(submitter.submit("notification.send", "invite-1", Map.of("n", 1), 1))
                .isTrue();
        assertThat(submitter.submit("notification.send", "invite-1", Map.of("n", 2), 1))
                .isFalse();

        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void scopesIdempotencyToTheTypeSoUnrelatedHandlersDoNotCollide() {
        String sharedBusinessKey = "shipment-42";

        assertThat(submitter.submit("notification.send", sharedBusinessKey, Map.of(), 1))
                .isTrue();
        assertThat(submitter.submit("evidence.record", sharedBusinessKey, Map.of(), 1))
                .isTrue();

        assertThat(rowCount()).isEqualTo(2);
    }

    /**
     * The failure this design exists to prevent. PostgreSQL aborts an entire
     * transaction on a unique violation — every later statement fails with
     * "current transaction is aborted" — so a plain INSERT would mean the
     * duplicate enqueue that idempotency is meant to tolerate destroys the
     * caller's business write instead.
     */
    @Test
    void aDuplicateEnqueueDoesNotAbortTheCallersTransaction() {
        submitter.submit("notification.send", "invite-1", Map.of("n", 1), 1);

        String written = transactionTemplate.execute(status -> {
            submitter.submit("notification.send", "invite-1", Map.of("n", 2), 1);

            // Any statement after the conflict. On an aborted transaction this
            // throws instead of returning.
            return jdbcTemplate.queryForObject("SELECT 'still usable'", String.class);
        });

        assertThat(written).isEqualTo("still usable");
        assertThat(rowCount()).isEqualTo(1);
    }
}
