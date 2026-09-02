package za.co.trademesh.shared.events.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import za.co.trademesh.shared.events.CorrelationContext;

@Import(OutboxWorkerTest.Handlers.class)
class OutboxWorkerTest extends OutboxTestSupport {

    static final String SUCCEEDS = "test.succeeds";
    static final String ALWAYS_FAILS = "test.always-fails";

    @Autowired
    private OutboxSubmitter submitter;

    @Autowired
    private OutboxWorker worker;

    @Autowired
    private Handlers.RecordingHandler recordingHandler;

    @BeforeEach
    void clearRecordings() {
        recordingHandler.handled().clear();
    }

    @Test
    void runsTheHandlerAndMarksTheMessageDone() {
        submitter.submit(SUCCEEDS, "job-1", Map.of("n", 1), 1);
        UUID id = onlyMessageId();

        assertThat(worker.pollOnce()).isEqualTo(1);

        assertThat(statusOf(id)).isEqualTo("DONE");
        assertThat(recordingHandler.handled()).hasSize(1);
    }

    @Test
    void handsTheHandlerThePayloadAndTheOriginatingCorrelation() {
        UUID correlationId = UUID.randomUUID();
        CorrelationContext.runWithin(
                correlationId, "user-7", () -> submitter.submit(SUCCEEDS, "job-1", Map.of("to", "a@b.test"), 1));

        worker.pollOnce();

        OutboxMessage handled = recordingHandler.handled().getFirst();
        assertThat(handled.payload()).contains("a@b.test");
        assertThat(handled.correlationId()).isEqualTo(correlationId);
        assertThat(handled.actor()).contains("user-7");
    }

    /**
     * The worker runs on its own thread with no inbound request, so correlation
     * has to come from the row. Without this the job's logs cannot be joined to
     * the request that caused it, which is the whole reason the column exists.
     */
    @Test
    void restoresTheCorrelationScopeAroundTheHandler() {
        UUID correlationId = UUID.randomUUID();
        CorrelationContext.runWithin(correlationId, "user-7", () -> submitter.submit(SUCCEEDS, "job-1", Map.of(), 1));

        worker.pollOnce();

        assertThat(recordingHandler.correlationSeenInsideHandler()).isEqualTo(correlationId);
        assertThat(recordingHandler.actorSeenInsideHandler()).isEqualTo("user-7");
    }

    @Test
    void returnsAFailedMessageToPendingWithAnIncrementedAttemptCount() {
        submitter.submit(ALWAYS_FAILS, "job-1", Map.of(), 1);
        UUID id = onlyMessageId();
        Instant before = Instant.now();

        worker.pollOnce();

        OutboxRepository.OutboxRow row = rowFor(id);
        assertThat(row.status()).isEqualTo("PENDING");
        assertThat(row.attempts()).isEqualTo(1);
        assertThat(row.availableAt()).isAfter(before);
        assertThat(row.lastError())
                .get()
                .asString()
                .contains(IllegalStateException.class.getName())
                .doesNotContain("handler refused");
        assertThat(row.claimedAt()).isEmpty();
    }

    @Test
    void doesNotReclaimAMessageWhoseRetryTimeHasNotArrived() {
        submitter.submit(ALWAYS_FAILS, "job-1", Map.of(), 1);

        assertThat(worker.pollOnce()).isEqualTo(1);
        assertThat(worker.pollOnce())
                .as("the backoff has not elapsed, so the second poll finds nothing due")
                .isZero();
    }

    @Test
    void marksAMessageDeadOnceItsAttemptsAreExhausted() {
        submitter.submit(ALWAYS_FAILS, "job-1", Map.of(), 1);
        UUID id = onlyMessageId();

        // maxAttempts is 8 by default; drive the row there directly rather than
        // waiting out eight real backoffs.
        jdbcTemplate.update("UPDATE outbox_message SET attempts = 7 WHERE id = ?", id);

        worker.pollOnce();

        assertThat(statusOf(id)).isEqualTo("DEAD");
        assertThat(rowFor(id).attempts()).isEqualTo(8);
    }

    /**
     * A missing handler is not a transient fault; retrying it seven more times
     * reaches the same end, later, having buried the cause in backoff.
     */
    @Test
    void marksAMessageDeadImmediatelyWhenNoHandlerIsRegistered() {
        submitter.submit("test.nobody-handles-this", "job-1", Map.of(), 1);
        UUID id = onlyMessageId();

        worker.pollOnce();

        assertThat(statusOf(id)).isEqualTo("DEAD");
        assertThat(rowFor(id).lastError()).get().asString().contains("no handler registered");
    }

    @Test
    void leavesADeadMessageInPlaceForInspection() {
        submitter.submit("test.nobody-handles-this", "job-1", Map.of(), 1);
        worker.pollOnce();

        assertThat(rowCount()).isEqualTo(1);
        assertThat(worker.pollOnce())
                .as("a DEAD message is never claimed again")
                .isZero();
    }

    private OutboxRepository.OutboxRow rowFor(UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT id, type, status, attempts, available_at, claimed_at, last_error "
                        + "FROM outbox_message WHERE id = ?",
                (rs, n) -> new OutboxRepository.OutboxRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("type"),
                        rs.getString("status"),
                        rs.getInt("attempts"),
                        rs.getTimestamp("available_at").toInstant(),
                        java.util.Optional.ofNullable(rs.getTimestamp("claimed_at"))
                                .map(java.sql.Timestamp::toInstant),
                        java.util.Optional.ofNullable(rs.getString("last_error"))),
                id);
    }

    @TestConfiguration
    static class Handlers {

        @Bean
        RecordingHandler recordingHandler() {
            return new RecordingHandler();
        }

        @Bean
        OutboxHandler alwaysFailingHandler() {
            return new OutboxHandler() {
                @Override
                public String type() {
                    return ALWAYS_FAILS;
                }

                @Override
                public void handle(OutboxMessage message) {
                    throw new IllegalStateException("handler refused this message");
                }
            };
        }

        static class RecordingHandler implements OutboxHandler {

            private final List<OutboxMessage> handled = new CopyOnWriteArrayList<>();
            private volatile UUID correlationSeenInsideHandler;
            private volatile String actorSeenInsideHandler;

            @Override
            public String type() {
                return SUCCEEDS;
            }

            @Override
            public void handle(OutboxMessage message) {
                correlationSeenInsideHandler = CorrelationContext.correlationId();
                actorSeenInsideHandler = CorrelationContext.actor().orElse(null);
                handled.add(message);
            }

            List<OutboxMessage> handled() {
                return handled;
            }

            UUID correlationSeenInsideHandler() {
                return correlationSeenInsideHandler;
            }

            String actorSeenInsideHandler() {
                return actorSeenInsideHandler;
            }
        }
    }
}
