package za.co.trademesh.shared.events.outbox;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Backoff is arithmetic, so it is tested as arithmetic — no container, no
 * database, and no waiting out a real delay.
 */
class OutboxBackoffTest {

    private static final Duration BASE = Duration.ofSeconds(2);
    private static final Duration CAP = Duration.ofMinutes(10);

    private final OutboxWorker worker = new OutboxWorker(
        null,
        List.of(),
        new OutboxProperties(50, 8, BASE, CAP, Duration.ofMinutes(5), true),
        Clock.systemUTC(),
        null);

    @Test
    void growsWithEachAttempt() {
        Duration first = worker.backoffFor(1);
        Duration fourth = worker.backoffFor(4);

        assertThat(fourth).isGreaterThan(first);
    }

    @Test
    void staysWithinHalfAndFullOfTheUnjitteredDelay() {
        // attempt 4 -> base 2s doubled three times = 16s; jitter keeps it in [8s, 16s]
        for (int i = 0; i < 200; i++) {
            assertThat(worker.backoffFor(4)).isBetween(Duration.ofSeconds(8), Duration.ofSeconds(16));
        }
    }

    /**
     * Without jitter every message that failed in the same poll retries at the
     * same instant, so an outage produces a synchronised burst that repeats for
     * as long as the outage lasts.
     */
    @Test
    void doesNotReturnTheSameDelayEveryTime() {
        List<Duration> delays = java.util.stream.IntStream.range(0, 100)
            .mapToObj(i -> worker.backoffFor(6))
            .distinct()
            .toList();

        assertThat(delays).hasSizeGreaterThan(1);
    }

    @Test
    void neverExceedsTheConfiguredCap() {
        for (int attempts : new int[] {1, 10, 40, 1000, Integer.MAX_VALUE}) {
            assertThat(worker.backoffFor(attempts))
                .as("attempt %s", attempts)
                .isLessThanOrEqualTo(CAP);
        }
    }

    /**
     * A large attempt count shifted past the width of a long would wrap to a
     * negative delay, and a negative delay makes the message immediately due
     * forever — a tight retry loop against a dependency that is already down.
     */
    @Test
    void staysPositiveAtExtremeAttemptCounts() {
        assertThat(worker.backoffFor(Integer.MAX_VALUE)).isPositive();
        assertThat(worker.backoffFor(0)).isPositive();
    }
}
