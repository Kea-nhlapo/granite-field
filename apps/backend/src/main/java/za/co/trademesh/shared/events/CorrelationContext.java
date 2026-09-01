package za.co.trademesh.shared.events;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Ambient correlation id and actor for the current unit of work.
 *
 * <p>Held in a {@link ThreadLocal} so that a service publishing an event does
 * not have to thread request metadata through every method signature between
 * the controller and the publish call.
 *
 * <p>The scope is always closed in a finally block via
 * {@link #runWithin(UUID, String, Runnable)} or {@link #callWithin}. Pooled
 * request threads are reused, so a scope left open leaks one request's actor
 * into the next request that happens to land on that thread — which is an
 * authorization-shaped bug, not a tidiness one.
 *
 * <p>Values do NOT propagate to threads spawned inside a scope, including the
 * outbox worker's. That is deliberate: the worker restores correlation from the
 * message row it claimed, which is the only correlation that is actually true
 * for that work.
 */
public final class CorrelationContext {

    private record Scope(UUID correlationId, String actor) {
    }

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private CorrelationContext() {
    }

    /**
     * Correlation id for the current scope, or a fresh one when there is no
     * scope. Falling back rather than throwing keeps a missing scope from
     * turning a working code path into a 500; the cost is a correlation id that
     * groups nothing, which is what an unscoped call deserves.
     */
    public static UUID correlationId() {
        Scope scope = CURRENT.get();
        return scope == null ? UUID.randomUUID() : scope.correlationId();
    }

    /** Actor for the current scope, empty for system-initiated work. */
    public static Optional<String> actor() {
        Scope scope = CURRENT.get();
        return scope == null ? Optional.empty() : Optional.ofNullable(scope.actor());
    }

    public static void runWithin(UUID correlationId, String actor, Runnable work) {
        callWithin(correlationId, actor, () -> {
            work.run();
            return null;
        });
    }

    public static <T> T callWithin(UUID correlationId, String actor, Supplier<T> work) {
        Scope previous = CURRENT.get();
        CURRENT.set(new Scope(correlationId, actor));
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
