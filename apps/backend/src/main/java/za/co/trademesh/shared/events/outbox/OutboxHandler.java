package za.co.trademesh.shared.events.outbox;

/**
 * Handles one outbox message type. Register by declaring a bean; the worker
 * dispatches on {@link #type()}.
 *
 * <p><strong>Handlers must be idempotent.</strong> Delivery is at-least-once,
 * not exactly-once. A handler that completes its side effect and then crashes
 * before the message is marked DONE will run again. There is no arrangement of
 * a single database that avoids this — marking DONE and performing an external
 * side effect cannot be one atomic act — so the contract is placed on the
 * handler rather than pretended away here.
 *
 * <p><strong>Handlers must be commutative.</strong> Messages are claimed in
 * batches by any number of workers with no per-aggregate ordering, so two
 * messages about the same entity may run concurrently and in either order. If
 * you need ordering, raise it before building on this rather than assuming it.
 */
public interface OutboxHandler {

    /** Stable wire name this handler serves; matched against the row's type. */
    String type();

    /**
     * Performs the work. Throwing schedules a retry; returning normally marks
     * the message DONE.
     */
    void handle(OutboxMessage message) throws Exception;
}
