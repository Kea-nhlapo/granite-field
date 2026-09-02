package za.co.trademesh.shared.events;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * The only supported way to publish a domain event.
 *
 * <p>Wraps {@link ApplicationEventPublisher} rather than exposing it so that
 * the envelope is stamped in exactly one place. A caller publishing the raw
 * event directly would produce an event with no correlation id, and nothing
 * would fail loudly enough to notice.
 *
 * <p>Publishing does not do the work. Listeners react after commit; anything
 * that can fail goes to the outbox.
 */
@Component
public class DomainEvents {

    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final String source;

    public DomainEvents(ApplicationEventPublisher publisher, Clock clock, EventProperties properties) {
        this.publisher = publisher;
        this.clock = clock;
        this.source = properties.source();
    }

    /**
     * Stamps an envelope and publishes.
     *
     * <p>Called inside the caller's transaction. Spring holds the event until
     * that transaction commits, so a rolled-back command publishes nothing.
     */
    public <E extends DomainEvent> EventEnvelope publish(E event) {
        return publish(event, CorrelationContext.actor());
    }

    /** Publishes with an actor already known by the application service. */
    public <E extends DomainEvent> EventEnvelope publish(E event, String actor) {
        return publish(event, Optional.ofNullable(actor));
    }

    private <E extends DomainEvent> EventEnvelope publish(E event, Optional<String> actor) {
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                event.type(),
                Instant.now(clock),
                actor,
                source,
                CorrelationContext.correlationId(),
                event.schemaVersion());

        publisher.publishEvent(new PublishedEvent<>(envelope, event));
        return envelope;
    }
}
