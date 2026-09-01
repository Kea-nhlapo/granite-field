package za.co.trademesh.shared.events;

import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;

/**
 * A domain event paired with the envelope stamped at publication.
 *
 * <p>This is what listeners actually receive. The event and its metadata travel
 * together so a listener that defers work can copy the correlation id and actor
 * onto the outbox message without reaching back into thread-local state — by
 * the time an after-commit listener runs, that state may belong to a different
 * request.
 *
 * <p>Implements {@link ResolvableTypeProvider} because {@code E} is erased at
 * runtime. Without it Spring cannot tell a {@code PublishedEvent<ShipmentX>}
 * from a {@code PublishedEvent<ShipmentY>}, so a listener declared for one
 * event type matches none and is never called — silently. Nothing logs, nothing
 * throws; the reaction simply does not happen. Supplying the resolvable type
 * from the runtime event instance is what makes typed listeners work at all.
 */
public record PublishedEvent<E extends DomainEvent>(EventEnvelope envelope, E event)
    implements ResolvableTypeProvider {

    @Override
    public ResolvableType getResolvableType() {
        return ResolvableType.forClassWithGenerics(
            PublishedEvent.class, ResolvableType.forInstance(event));
    }
}
