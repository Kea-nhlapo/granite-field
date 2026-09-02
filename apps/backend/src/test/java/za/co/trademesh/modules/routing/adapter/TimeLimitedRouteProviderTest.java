package za.co.trademesh.modules.routing.adapter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.routing.domain.Coordinate;
import za.co.trademesh.modules.routing.domain.RouteCandidateSet;
import za.co.trademesh.modules.routing.domain.RouteProviderException;
import za.co.trademesh.modules.routing.domain.RouteRequest;
import za.co.trademesh.modules.routing.domain.RoutingVehicleLimits;
import za.co.trademesh.modules.routing.port.RouteProvider;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeLimitedRouteProviderTest {

    private static final RouteRequest REQUEST = new RouteRequest(
        new Coordinate(-26.20, 28.05),
        new Coordinate(-25.75, 28.19),
        List.of(),
        new RoutingVehicleLimits(4200, 26000),
        Set.of());

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void releaseAndShutDown() {
        release.countDown();
        executor.shutdownNow();
    }

    /**
     * A latch rather than a sleep: the stub blocks until the test releases it, so
     * the test cannot pass by accident on a slow machine or flake on a fast one.
     */
    @Test
    void failsWithAClearErrorWhenTheProviderExceedsItsTimeout() {
        RouteProvider blocked = request -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        };

        RouteProvider timeLimited =
            new TimeLimitedRouteProvider(blocked, Duration.ofMillis(50), executor);

        assertThatThrownBy(() -> timeLimited.findCandidates(REQUEST))
            .isInstanceOf(RouteProviderException.class)
            .hasMessageContaining("timed out");
    }

    @Test
    void passesThroughTheResultWhenTheProviderRespondsInTime() {
        RouteCandidateSet expected = new DeterministicRouteProvider().findCandidates(REQUEST);
        RouteProvider prompt = request -> expected;

        RouteProvider timeLimited =
            new TimeLimitedRouteProvider(prompt, Duration.ofSeconds(5), executor);

        assertThat(timeLimited.findCandidates(REQUEST)).isSameAs(expected);
    }

    @Test
    void wrapsAProviderFailureRatherThanLettingItEscapeRaw() {
        RouteProvider broken = request -> {
            throw new IllegalStateException("upstream exploded with secret=abc123");
        };

        RouteProvider timeLimited =
            new TimeLimitedRouteProvider(broken, Duration.ofSeconds(5), executor);

        assertThatThrownBy(() -> timeLimited.findCandidates(REQUEST))
            .isInstanceOf(RouteProviderException.class);
    }

    @Test
    void rejectsATimeoutThatWouldTruncateToZeroMilliseconds() {
        assertThatThrownBy(() -> new TimeLimitedRouteProvider(
            new DeterministicRouteProvider(), Duration.ofNanos(500_000), executor))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one millisecond");
    }

    @Test
    void doesNotLeaveTheCallerBlockedAfterATimeout() throws InterruptedException {
        RouteProvider blocked = request -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        };

        RouteProvider timeLimited =
            new TimeLimitedRouteProvider(blocked, Duration.ofMillis(50), executor);

        long start = System.nanoTime();
        assertThatThrownBy(() -> timeLimited.findCandidates(REQUEST))
            .isInstanceOf(RouteProviderException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - start))
            .as("the caller returns at the timeout, not when the delegate finishes")
            .isLessThan(Duration.ofSeconds(5));
    }
}
