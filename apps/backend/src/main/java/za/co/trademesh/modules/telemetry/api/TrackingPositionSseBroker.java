package za.co.trademesh.modules.telemetry.api;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import za.co.trademesh.modules.telemetry.application.TrackingProperties;
import za.co.trademesh.modules.telemetry.domain.TelemetryLivePosition;
import za.co.trademesh.modules.telemetry.events.TelemetryEvent;
import za.co.trademesh.shared.events.PublishedEvent;

@Component
class TrackingPositionSseBroker {

    private final ConcurrentHashMap<UUID, CopyOnWriteArraySet<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final TrackingProperties properties;

    TrackingPositionSseBroker(TrackingProperties properties) {
        this.properties = properties;
    }

    SseEmitter subscribe(UUID shipmentId, java.util.Optional<TelemetryLivePosition> current) {
        SseEmitter emitter = new SseEmitter(properties.streamTimeout().toMillis());
        subscribers
                .computeIfAbsent(shipmentId, ignored -> new CopyOnWriteArraySet<>())
                .add(emitter);
        Runnable remove = () -> remove(shipmentId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ignored -> remove.run());
        current.ifPresent(position -> send(
                shipmentId,
                emitter,
                position.readingId().toString(),
                TelemetryContracts.PositionUpdateResponse.from(position)));
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(PublishedEvent<?> published) {
        if (!(published.event() instanceof TelemetryEvent.ReadingAccepted reading)
                || reading.latitude() == null
                || reading.longitude() == null) {
            return;
        }
        Set<SseEmitter> current = subscribers.get(reading.shipmentId());
        if (current == null) {
            return;
        }
        var response = TelemetryContracts.PositionUpdateResponse.from(reading);
        current.forEach(emitter ->
                send(reading.shipmentId(), emitter, reading.readingId().toString(), response));
    }

    private void send(UUID shipmentId, SseEmitter emitter, String eventId, Object data) {
        try {
            emitter.send(SseEmitter.event().name("position").id(eventId).data(data));
        } catch (IOException | IllegalStateException disconnected) {
            remove(shipmentId, emitter);
        }
    }

    private void remove(UUID shipmentId, SseEmitter emitter) {
        subscribers.computeIfPresent(shipmentId, (ignored, current) -> {
            current.remove(emitter);
            return current.isEmpty() ? null : current;
        });
    }
}
