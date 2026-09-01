package za.co.trademesh.modules.routing.adapter;

import za.co.trademesh.modules.routing.domain.RouteCandidateSet;
import za.co.trademesh.modules.routing.domain.RouteProviderException;
import za.co.trademesh.modules.routing.domain.RouteRequest;
import za.co.trademesh.modules.routing.port.RouteProvider;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Bounds how long a caller waits on a routing engine.
 *
 * <p>IMPORTANT — a timed-out call is ABANDONED, not stopped. Java cannot halt a
 * running computation; the delegate keeps executing on its own thread until it
 * finishes or notices an interrupt. What this guarantees is that the CALLER
 * stops waiting, which is what protects the request path. A delegate doing real
 * I/O should still set its own socket timeouts so the abandoned work eventually
 * ends rather than accumulating.
 *
 * <p>Provider exceptions are wrapped in RouteProviderException so no adapter
 * detail — vendor names, URLs, anything in an upstream message — escapes to the
 * domain.
 */
public class TimeLimitedRouteProvider implements RouteProvider {

    private final RouteProvider delegate;
    private final Duration timeout;
    private final ExecutorService executor;

    public TimeLimitedRouteProvider(RouteProvider delegate, Duration timeout, ExecutorService executor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate is required");
        this.timeout = Objects.requireNonNull(timeout, "timeout is required");
        this.executor = Objects.requireNonNull(executor, "executor is required");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive, was " + timeout);
        }
    }

    @Override
    public RouteCandidateSet findCandidates(RouteRequest request) {
        CompletableFuture<RouteCandidateSet> pending =
            CompletableFuture.supplyAsync(() -> delegate.findCandidates(request), executor);

        try {
            return pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // Interrupts the delegate if it is interruptible; if it is not, the
            // work is abandoned and this call returns anyway.
            pending.cancel(true);
            throw new RouteProviderException(
                "route provider timed out after " + timeout.toMillis() + "ms", e);
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new RouteProviderException("interrupted while waiting for the route provider", e);
        } catch (CompletionException | java.util.concurrent.ExecutionException e) {
            throw new RouteProviderException("route provider failed", e.getCause());
        }
    }
}
