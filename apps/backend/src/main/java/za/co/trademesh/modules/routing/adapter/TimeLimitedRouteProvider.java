package za.co.trademesh.modules.routing.adapter;

import za.co.trademesh.modules.routing.domain.RouteCandidateSet;
import za.co.trademesh.modules.routing.domain.RouteProviderException;
import za.co.trademesh.modules.routing.domain.RouteRequest;
import za.co.trademesh.modules.routing.port.RouteProvider;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
 * I/O should still set its own socket timeouts so abandoned work eventually
 * ends rather than accumulating.
 *
 * <p>Failures are translated to {@link RouteProviderException} so callers have
 * one type to handle. Note what this does NOT do: the original exception is kept
 * as the cause, so an upstream message still travels in the stack trace. If a
 * future HTTP adapter can put a credential in an exception message, that adapter
 * is responsible for scrubbing it — this class does not.
 *
 * <p>The executor is supplied by the caller and its lifecycle belongs to
 * whoever created it; see RoutingConfiguration.
 */
public class TimeLimitedRouteProvider implements RouteProvider {

    private final RouteProvider delegate;
    private final Duration timeout;
    private final ExecutorService executor;

    public TimeLimitedRouteProvider(RouteProvider delegate, Duration timeout, ExecutorService executor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate is required");
        this.timeout = Objects.requireNonNull(timeout, "timeout is required");
        this.executor = Objects.requireNonNull(executor, "executor is required");
        // Below a millisecond, toMillis() truncates to zero and EVERY call would
        // time out instantly, which looks like a broken provider rather than a
        // misconfigured timeout.
        if (timeout.toMillis() < 1) {
            throw new IllegalArgumentException("timeout must be at least one millisecond, was " + timeout);
        }
    }

    @Override
    public RouteCandidateSet findCandidates(RouteRequest request) {
        CompletableFuture<RouteCandidateSet> pending =
            CompletableFuture.supplyAsync(() -> delegate.findCandidates(request), executor);

        try {
            return pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new RouteProviderException(
                "route provider timed out after " + timeout.toMillis() + "ms", e);
        } catch (InterruptedException e) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new RouteProviderException("interrupted while waiting for the route provider", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            // Rethrow rather than wrap again: a second wrapper buries the useful
            // message one level deeper for no gain.
            if (cause instanceof RouteProviderException alreadyTranslated) {
                throw alreadyTranslated;
            }
            throw new RouteProviderException("route provider failed", cause);
        }
    }
}
