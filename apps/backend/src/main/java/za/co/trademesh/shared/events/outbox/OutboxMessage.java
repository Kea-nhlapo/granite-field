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
 *
 * <p>{@code claimedAt} is the timestamp recorded in the row when this worker
 * claimed the message. It is passed back to outcome methods
 * ({@link OutboxRepository#markDone}, {@link OutboxRepository#markForRetry},
 * {@link OutboxRepository#markDead}) as an ownership token: a stale worker whose
 * claim the reaper has revoked and whose message has since been re-claimed by
 * another worker will find a different {@code claimed_at} in the row and its
 * outcome update will silently no-op rather than corrupting the live claim.
 */
public record OutboxMessage(
    UUID id,
    String type,
    String payload,
    String idempotencyKey,
    int attempts,
    Instant claimedAt,
    EventEnvelope envelope,
    Instant availableAt) {

    public UUID correlationId() {
        return envelope.correlationId();
    }

    public Optional<String> actor() {
        return envelope.actor();
    }
}
