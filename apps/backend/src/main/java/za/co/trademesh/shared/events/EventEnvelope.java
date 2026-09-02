package za.co.trademesh.shared.events;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Metadata carried alongside every domain event and every outbox message
 * (issue #4 acceptance criteria).
 *
 * <p>The envelope is separate from the event body so that the metadata shape is
 * fixed once here rather than re-declared, and re-forgotten, by each event.
 *
 * @param eventId       unique per publication; two publications of an
 *                      identical body are still two distinct events
 * @param type          stable wire name, from {@link DomainEvent#type()}
 * @param occurredAt    when the fact happened, not when it was serialised
 * @param actor         who caused it; empty for system-initiated work such as
 *                      a scheduled sweep, where inventing a sentinel would
 *                      only drift between callers
 * @param source        the component that published it, for tracing a message
 *                      back to its origin
 * @param correlationId groups every event and message caused by one inbound
 *                      request, so a failure can be traced to what triggered it
 * @param schemaVersion payload shape version, from
 *                      {@link DomainEvent#schemaVersion()}
 */
public record EventEnvelope(
    UUID eventId,
    String type,
    Instant occurredAt,
    Optional<String> actor,
    String source,
    UUID correlationId,
    int schemaVersion) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(correlationId, "correlationId");
        requireText(type, "type");
        requireText(source, "source");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be at least 1");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    /**
     * Stamps an envelope for something happening right now, reading actor and
     * correlation id from the ambient {@link CorrelationContext}.
     *
     * <p>{@link DomainEvents#publish} and {@link
     * za.co.trademesh.shared.events.outbox.OutboxSubmitter#submitAt} both need
     * exactly this: a fresh event id, the current instant, and the calling
     * scope's correlation metadata. Centralising it here means a field can
     * never drift between the two — a scoped field added to one and forgotten
     * on the other, discovered only when correlation goes missing from half of
     * production's traces.
     */
    public static EventEnvelope stampNow(Clock clock, String type, String source, int schemaVersion) {
        return new EventEnvelope(
            UUID.randomUUID(),
            type,
            Instant.now(clock),
            CorrelationContext.actor(),
            source,
            CorrelationContext.correlationId(),
            schemaVersion);
    }
}
