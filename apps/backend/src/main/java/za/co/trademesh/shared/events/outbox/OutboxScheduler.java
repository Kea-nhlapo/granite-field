package za.co.trademesh.shared.events.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives {@link OutboxWorker} on a timer.
 *
 * <p>Separate from the worker so that the worker stays directly callable. A
 * test that drives {@code pollOnce()} itself asserts on an exact state; a test
 * that waits for a scheduler asserts on timing, and eventually fails on a busy
 * CI machine for reasons unrelated to the code.
 *
 * <p>{@code fixedDelay} rather than {@code fixedRate}: with fixedRate a poll
 * that takes longer than the interval causes the next one to start immediately,
 * and a slow batch turns into overlapping polls competing for the pool.
 * Safe under SKIP LOCKED, but pointless load exactly when the system is
 * already struggling.
 */
@Component
@ConditionalOnProperty(prefix = "trademesh.outbox", name = "enabled", matchIfMissing = true)
public class OutboxScheduler {

    private final OutboxWorker worker;

    public OutboxScheduler(OutboxWorker worker) {
        this.worker = worker;
    }

    @Scheduled(
            fixedDelayString = "${trademesh.outbox.poll-interval:PT1S}",
            initialDelayString = "${trademesh.outbox.initial-delay:PT5S}")
    public void poll() {
        worker.pollOnce();
    }

    /**
     * Runs far less often than the poll. Reaping only matters after a worker
     * dies, and the visibility timeout already bounds how long a message waits.
     */
    @Scheduled(
            fixedDelayString = "${trademesh.outbox.reap-interval:PT1M}",
            initialDelayString = "${trademesh.outbox.reap-initial-delay:PT1M}")
    public void reap() {
        worker.reapOnce();
    }
}
