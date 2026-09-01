package za.co.trademesh.shared.events.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import za.co.trademesh.support.PostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the assumption every other outbox test rests on.
 *
 * <p>Spring caches an application context for the whole JVM run. One context
 * with the scheduler enabled leaves a worker polling every second for the rest
 * of the suite, claiming messages that other tests inserted and are about to
 * assert on — a failure that appears in whichever test happens to lose the
 * race, never in the one that caused it.
 *
 * <p>src/test/resources/application.properties turns the scheduler off. This
 * test is what keeps that file from being deleted by someone who cannot see
 * what it does.
 */
class OutboxSchedulerDisabledInTestsTest extends PostgresIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void theSchedulerDoesNotRunDuringTests() {
        assertThat(context.getBeanNamesForType(OutboxScheduler.class))
            .as("trademesh.outbox.enabled must stay false in src/test/resources/application.properties")
            .isEmpty();
    }

    @Test
    void theWorkerIsStillAvailableToBeDrivenDirectly() {
        assertThat(context.getBeanNamesForType(OutboxWorker.class)).hasSize(1);
    }
}
