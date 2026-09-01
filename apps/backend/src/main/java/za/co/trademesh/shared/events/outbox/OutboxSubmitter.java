package za.co.trademesh.shared.events.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import za.co.trademesh.shared.events.CorrelationContext;
import za.co.trademesh.shared.events.EventEnvelope;
import za.co.trademesh.shared.events.EventProperties;

/**
 * Enqueues work that can fail or take time.
 *
 * <p>Call this inside the transaction that makes the business change. The
 * message and the change then commit or roll back together, which is the whole
 * point of an outbox: there is no window in which the change is durable but the
 * follow-up work was never recorded.
 *
 * <p>Jackson here is 3.x under {@code tools.jackson}, not
 * {@code com.fasterxml.jackson}. Boot 4 moved it, and Jackson 3 also made the
 * exception hierarchy unchecked, so serialisation failures surface as runtime
 * exceptions that roll the caller's transaction back rather than as a checked
 * signature.
 */
@Component
public class OutboxSubmitter {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String source;

    public OutboxSubmitter(
        OutboxRepository repository,
        ObjectMapper objectMapper,
        Clock clock,
        EventProperties eventProperties) {

        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.source = eventProperties.source();
    }

    /**
     * Enqueues a message for immediate processing.
     *
     * @param type           stable wire name a handler is registered for
     * @param idempotencyKey natural key for this unit of work, unique within
     *                       the type. Enqueueing the same key twice is a no-op,
     *                       so a retried request does not double the work.
     * @param payload        serialised as JSON; must be a value object
     * @param schemaVersion  payload shape version
     * @return true if this call queued the message, false if it was already
     *         queued. Callers rarely need this; it exists so a test can assert
     *         deduplication happened rather than infer it.
     */
    public boolean submit(String type, String idempotencyKey, Object payload, int schemaVersion) {
        return submitAt(type, idempotencyKey, payload, schemaVersion, Instant.now(clock));
    }

    /** Enqueues a message that must not be processed before {@code availableAt}. */
    public boolean submitAt(
        String type,
        String idempotencyKey,
        Object payload,
        int schemaVersion,
        Instant availableAt) {

        EventEnvelope envelope = new EventEnvelope(
            UUID.randomUUID(),
            type,
            Instant.now(clock),
            CorrelationContext.actor(),
            source,
            CorrelationContext.correlationId(),
            schemaVersion);

        return repository.enqueue(
            envelope.eventId(),
            type,
            objectMapper.writeValueAsString(payload),
            idempotencyKey,
            envelope,
            availableAt);
    }
}
