package za.co.trademesh.shared.events;

/**
 * Marker for a typed, in-process domain event.
 *
 * <p>Events describe something that has already happened, so they are named in
 * the past tense and carry no behaviour. Publishing one is not a request for
 * work: if the reaction may fail or take time, the listener enqueues an outbox
 * message rather than doing the work itself.
 *
 * <p>Implementations must be immutable records. A listener runs after the
 * publisher's transaction has committed and cannot signal failure back to it,
 * so a mutable event would let one listener silently change what the next
 * listener sees.
 */
public interface DomainEvent {

    /**
     * Stable wire name for this event type, used as the outbox message type
     * when a listener defers work. Deliberately not the class name: renaming a
     * class must not orphan rows already written to the outbox.
     */
    String type();

    /**
     * Version of this event's payload shape. Increment when a field changes
     * meaning or disappears, so a consumer reading a stored payload can tell
     * which shape it is looking at.
     */
    int schemaVersion();
}
