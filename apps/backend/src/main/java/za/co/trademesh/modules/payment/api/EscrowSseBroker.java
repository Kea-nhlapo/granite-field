package za.co.trademesh.modules.payment.api;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import za.co.trademesh.modules.payment.application.EscrowProperties;
import za.co.trademesh.modules.payment.application.EscrowService;
import za.co.trademesh.modules.payment.application.EscrowSnapshot;
import za.co.trademesh.modules.payment.events.PaymentEvent;
import za.co.trademesh.shared.events.PublishedEvent;

@Component
class EscrowSseBroker {

    private final ConcurrentHashMap<UUID, CopyOnWriteArraySet<Subscriber>> subscribers = new ConcurrentHashMap<>();
    private final EscrowService escrows;
    private final EscrowProperties properties;

    EscrowSseBroker(EscrowService escrows, EscrowProperties properties) {
        this.escrows = escrows;
        this.properties = properties;
    }

    SseEmitter subscribe(EscrowSnapshot snapshot) {
        SseEmitter emitter = new SseEmitter(properties.streamTimeout().toMillis());
        Subscriber subscriber = new Subscriber(snapshot.businessId(), emitter);
        subscribers
                .computeIfAbsent(snapshot.shipmentId(), ignored -> new CopyOnWriteArraySet<>())
                .add(subscriber);
        Runnable remove = () -> remove(snapshot.shipmentId(), subscriber);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ignored -> remove.run());
        send(snapshot.shipmentId(), subscriber, snapshot);
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(PublishedEvent<?> published) {
        if (!(published.event() instanceof PaymentEvent paymentEvent)) {
            return;
        }
        EventKey key = key(paymentEvent);
        Set<Subscriber> current = subscribers.get(key.shipmentId());
        if (current == null || current.isEmpty()) {
            return;
        }
        EscrowSnapshot snapshot;
        try {
            snapshot = escrows.get(key.businessId(), key.shipmentId());
        } catch (RuntimeException unavailable) {
            return;
        }
        current.stream()
                .filter(subscriber -> subscriber.businessId().equals(key.businessId()))
                .forEach(subscriber -> send(key.shipmentId(), subscriber, snapshot));
    }

    private void send(UUID shipmentId, Subscriber subscriber, EscrowSnapshot snapshot) {
        try {
            subscriber
                    .emitter()
                    .send(SseEmitter.event()
                            .name("escrow-status")
                            .id(snapshot.updatedAt().toString())
                            .data(EscrowContracts.EscrowResponse.from(snapshot)));
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

    private static EventKey key(PaymentEvent event) {
        return switch (event) {
            case PaymentEvent.LockRequested value -> new EventKey(value.shipmentId(), value.businessId());
            case PaymentEvent.LockPending value -> new EventKey(value.shipmentId(), value.businessId());
            case PaymentEvent.Locked value -> new EventKey(value.shipmentId(), value.businessId());
            case PaymentEvent.LockFailed value -> new EventKey(value.shipmentId(), value.businessId());
            case PaymentEvent.ReleaseRequested value -> new EventKey(value.shipmentId(), value.businessId());
            case PaymentEvent.ReleasePending value -> new EventKey(value.shipmentId(), value.businessId());
            case PaymentEvent.Released value -> new EventKey(value.shipmentId(), value.businessId());
            case PaymentEvent.ReleaseFailed value -> new EventKey(value.shipmentId(), value.businessId());
        };
    }

    private record Subscriber(UUID businessId, SseEmitter emitter) {}

    private record EventKey(UUID shipmentId, UUID businessId) {}
}
