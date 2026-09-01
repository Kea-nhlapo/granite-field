package za.co.trademesh.shared.events.outbox;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import za.co.trademesh.shared.events.EventEnvelope;

/**
 * One claimed outbox row, as handed to a handler.
 *
 * <p>Carries the envelope so a handler can restore the originating correlation
 * id and actor. Work done by the worker is attributed to whoever caused the
 * message, not to the worker thread.
 */
public record OutboxMessage(
    UUID id,
    String type,
    String payload,
    String idempotencyKey,
    int attempts,
    EventEnvelope envelope,
    Instant availableAt) {

    public UUID correlationId() {
        return envelope.correlationId();
    }

    public Optional<String> actor() {
        return envelope.actor();
    }
}
