package za.co.trademesh.shared.events;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The envelope is the acceptance criterion "event payloads include an ID, type,
 * timestamp, actor, source, correlation ID, and schema version". These tests
 * assert the fields cannot be absent, because a criterion that only holds when
 * callers remember it is not a criterion.
 */
class EventEnvelopeTest {

    private static EventEnvelope valid() {
        return new EventEnvelope(
            UUID.randomUUID(),
            "shipment.dispatched",
            Instant.parse("2026-09-01T18:00:00Z"),
            Optional.of("user-7"),
            "trademesh-backend",
            UUID.randomUUID(),
            1);
    }

    @Test
    void carriesEveryRequiredMetadataField() {
        EventEnvelope envelope = valid();

        assertThat(envelope.eventId()).isNotNull();
        assertThat(envelope.type()).isEqualTo("shipment.dispatched");
        assertThat(envelope.occurredAt()).isEqualTo(Instant.parse("2026-09-01T18:00:00Z"));
        assertThat(envelope.actor()).contains("user-7");
        assertThat(envelope.source()).isEqualTo("trademesh-backend");
        assertThat(envelope.correlationId()).isNotNull();
        assertThat(envelope.schemaVersion()).isEqualTo(1);
    }

    @Test
    void allowsAnAbsentActorForSystemInitiatedWork() {
        EventEnvelope envelope = new EventEnvelope(
            UUID.randomUUID(), "outbox.swept", Instant.now(),
            Optional.empty(), "trademesh-backend", UUID.randomUUID(), 1);

        assertThat(envelope.actor()).isEmpty();
    }

    @Test
    void rejectsAnAbsentEventId() {
        assertThatThrownBy(() -> new EventEnvelope(
            null, "t", Instant.now(), Optional.empty(), "s", UUID.randomUUID(), 1))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsAnAbsentCorrelationId() {
        assertThatThrownBy(() -> new EventEnvelope(
            UUID.randomUUID(), "t", Instant.now(), Optional.empty(), "s", null, 1))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsABlankTypeRatherThanStoringAnUnroutableMessage() {
        assertThatThrownBy(() -> new EventEnvelope(
            UUID.randomUUID(), "  ", Instant.now(), Optional.empty(), "s", UUID.randomUUID(), 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("type");
    }

    @Test
    void rejectsABlankSource() {
        assertThatThrownBy(() -> new EventEnvelope(
            UUID.randomUUID(), "t", Instant.now(), Optional.empty(), "", UUID.randomUUID(), 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("source");
    }

    @Test
    void rejectsASchemaVersionBelowOneSoAnUnsetFieldCannotPass() {
        assertThatThrownBy(() -> new EventEnvelope(
            UUID.randomUUID(), "t", Instant.now(), Optional.empty(), "s", UUID.randomUUID(), 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("schemaVersion");
    }
}
