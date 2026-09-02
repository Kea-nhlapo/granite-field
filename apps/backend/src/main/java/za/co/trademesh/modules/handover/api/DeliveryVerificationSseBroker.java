package za.co.trademesh.modules.handover.api;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import za.co.trademesh.modules.handover.application.HandoverProperties;
import za.co.trademesh.modules.handover.application.HandoverService;
import za.co.trademesh.modules.handover.events.HandoverEvent;
import za.co.trademesh.shared.events.PublishedEvent;

@Component
class DeliveryVerificationSseBroker {

    private final ConcurrentHashMap<UUID, CopyOnWriteArraySet<Subscriber>> subscribers = new ConcurrentHashMap<>();
    private final HandoverService handovers;
    private final HandoverProperties properties;

    DeliveryVerificationSseBroker(HandoverService handovers, HandoverProperties properties) {
        this.handovers = handovers;
        this.properties = properties;
    }

    SseEmitter subscribe(HandoverService.DeliveryStatus status) {
        SseEmitter emitter = new SseEmitter(properties.streamTimeout().toMillis());
        Subscriber subscriber = new Subscriber(status.businessId(), emitter);
        subscribers
                .computeIfAbsent(status.shipmentId(), ignored -> new CopyOnWriteArraySet<>())
                .add(subscriber);
        Runnable remove = () -> remove(status.shipmentId(), subscriber);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ignored -> remove.run());
        send(status.shipmentId(), subscriber, status);
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(PublishedEvent<?> published) {
        if (!(published.event() instanceof HandoverEvent.HandoverFinalized)
                && !(published.event() instanceof HandoverEvent.DisputeResolved)) {
            return;
        }
        UUID shipmentId =
                switch (published.event()) {
                    case HandoverEvent.HandoverFinalized event -> event.shipmentId();
                    case HandoverEvent.DisputeResolved event -> event.shipmentId();
                    default -> throw new IllegalStateException("Unsupported delivery verification event");
                };
        Set<Subscriber> current = subscribers.get(shipmentId);
        if (current == null || current.isEmpty()) {
            return;
        }
        current.forEach(subscriber -> {
            try {
                send(shipmentId, subscriber, handovers.deliveryStatus(subscriber.businessId(), shipmentId));
            } catch (RuntimeException unavailable) {
                remove(shipmentId, subscriber);
            }
        });
    }

    private void send(UUID shipmentId, Subscriber subscriber, HandoverService.DeliveryStatus status) {
        try {
            subscriber
                    .emitter()
                    .send(SseEmitter.event()
                            .name("delivery-verification")
                            .id(status.updatedAt().toString())
                            .data(DeliveryVerificationContracts.StatusResponse.from(status)));
        } catch (IOException | IllegalStateException disconnected) {
            remove(shipmentId, subscriber);
        }
    }

    private void remove(UUID shipmentId, Subscriber subscriber) {
        subscribers.computeIfPresent(shipmentId, (ignored, current) -> {
            current.remove(subscriber);
            return current.isEmpty() ? null : current;
        });
    }

    private record Subscriber(UUID businessId, SseEmitter emitter) {}
}
