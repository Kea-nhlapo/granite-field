package za.co.trademesh.modules.notification.application;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import za.co.trademesh.shared.events.outbox.OutboxHandler;
import za.co.trademesh.shared.events.outbox.OutboxMessage;

@Component
class MobileDeliveryHandler implements OutboxHandler {

    private final ObjectMapper objectMapper;
    private final MobileDeliveryCoordinator coordinator;

    MobileDeliveryHandler(ObjectMapper objectMapper, MobileDeliveryCoordinator coordinator) {
        this.objectMapper = objectMapper;
        this.coordinator = coordinator;
    }

    @Override
    public String type() {
        return MobileDeliveryRequested.TYPE;
    }

    @Override
    public void handle(OutboxMessage message) throws Exception {
        MobileDeliveryRequested requested = objectMapper.readValue(message.payload(), MobileDeliveryRequested.class);
        coordinator.deliver(requested.notificationId(), message.id(), message.attempts());
    }
}
