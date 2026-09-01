package za.co.trademesh.shared.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.trademesh.support.PostgresIntegrationTest;

/**
 * Covers acceptance criteria 2 and 5: after-commit reactions use typed events,
 * and a failed handler cannot roll back a transaction that already committed.
 */
@Import(AfterCommitEventTest.Listeners.class)
class AfterCommitEventTest extends PostgresIntegrationTest {

    record ThingHappened(String detail) implements DomainEvent {
        @Override
        public String type() {
            return "test.thing-happened";
        }

        @Override
        public int schemaVersion() {
            return 1;
        }
    }

    @Autowired
    private DomainEvents domainEvents;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Listeners.Recorder recorder;

    @BeforeEach
    void createProbeTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS after_commit_probe (note text)");
        jdbcTemplate.execute("DELETE FROM after_commit_probe");
        recorder.reset();
    }

    @AfterEach
    void dropProbeTable() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS after_commit_probe");
    }

    @Test
    void deliversTheEventWithItsEnvelopeAfterTheTransactionCommits() {
        UUID correlationId = UUID.randomUUID();

        CorrelationContext.runWithin(
                correlationId,
                "user-7",
                () -> transactionTemplate.executeWithoutResult(status -> {
                    jdbcTemplate.update("INSERT INTO after_commit_probe VALUES ('written')");
                    domainEvents.publish(new ThingHappened("detail"));

                    assertThat(recorder.received())
                            .as("the listener must not run before the commit")
                            .isEmpty();
                }));

        assertThat(recorder.received()).hasSize(1);
        PublishedEvent<ThingHappened> delivered = recorder.received().getFirst();
        assertThat(delivered.event().detail()).isEqualTo("detail");
        assertThat(delivered.envelope().correlationId()).isEqualTo(correlationId);
        assertThat(delivered.envelope().actor()).contains("user-7");
        assertThat(delivered.envelope().type()).isEqualTo("test.thing-happened");
        assertThat(delivered.envelope().eventId()).isNotNull();
    }

    @Test
    void publishesNothingWhenTheTransactionRollsBack() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    domainEvents.publish(new ThingHappened("never happened"));
                    throw new IllegalStateException("command failed");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(recorder.received())
                .as("an event describes something that happened; a rolled-back command did not")
                .isEmpty();
    }

    /**
     * Acceptance criterion 5. The commit is already durable by the time an
     * after-commit listener runs, so there is nothing left to roll back — the
     * exception can only be logged. This test exists because the opposite is
     * the intuitive expectation, and a listener author who assumes their
     * failure aborts the command will write code that quietly loses work.
     */
    @Test
    void aThrowingListenerCannotUndoTheCommittedWrite() {
        recorder.failOnNextEvent();

        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("INSERT INTO after_commit_probe VALUES ('survives')");
            domainEvents.publish(new ThingHappened("boom"));
        });

        String surviving = jdbcTemplate.queryForObject("SELECT note FROM after_commit_probe", String.class);

        assertThat(surviving).isEqualTo("survives");
    }

    /**
     * A second listener still runs after the first one throws only if the
     * failure is contained. Asserting it here fixes the behaviour: one broken
     * listener must not silently disable every listener registered after it.
     */
    @Test
    void oneFailingListenerDoesNotStopTheOthers() {
        recorder.failOnNextEvent();

        transactionTemplate.executeWithoutResult(status -> domainEvents.publish(new ThingHappened("boom")));

        assertThat(recorder.secondListenerRan()).isTrue();
    }

    @TestConfiguration
    static class Listeners {

        @Bean
        Recorder recorder() {
            return new Recorder();
        }

        static class Recorder {

            private final List<PublishedEvent<ThingHappened>> received = new CopyOnWriteArrayList<>();
            private volatile boolean failNext;
            private volatile boolean secondListenerRan;

            void reset() {
                received.clear();
                failNext = false;
                secondListenerRan = false;
            }

            void failOnNextEvent() {
                failNext = true;
            }

            List<PublishedEvent<ThingHappened>> received() {
                return received;
            }

            boolean secondListenerRan() {
                return secondListenerRan;
            }

            /**
             * REQUIRES_NEW is deliberate. An AFTER_COMMIT listener runs with no
             * active transaction, so a listener that needs one must open its
             * own — the single most common way this mechanism is got wrong.
             */
            @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
            @Transactional(propagation = Propagation.REQUIRES_NEW)
            public void record(PublishedEvent<ThingHappened> event) {
                received.add(event);
                if (failNext) {
                    failNext = false;
                    throw new IllegalStateException("listener refused the event");
                }
            }

            @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
            public void alsoRun(PublishedEvent<ThingHappened> event) {
                secondListenerRan = true;
            }
        }
    }
}
