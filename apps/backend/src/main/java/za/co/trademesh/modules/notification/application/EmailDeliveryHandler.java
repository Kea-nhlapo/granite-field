package za.co.trademesh.modules.notification.application;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import za.co.trademesh.shared.events.outbox.OutboxHandler;
import za.co.trademesh.shared.events.outbox.OutboxMessage;

@Component
public class EmailDeliveryHandler implements OutboxHandler {

    private final ObjectMapper objectMapper;
    private final EmailDeliveryCoordinator coordinator;

    public EmailDeliveryHandler(ObjectMapper objectMapper, EmailDeliveryCoordinator coordinator) {
        this.objectMapper = objectMapper;
        this.coordinator = coordinator;
    }

    @Override
    public String type() {
        return EmailDeliveryRequested.TYPE;
    }

    @Override
    public void handle(OutboxMessage message) throws Exception {
        EmailDeliveryRequested request = objectMapper.readValue(message.payload(), EmailDeliveryRequested.class);
        coordinator.deliver(request.notificationId(), message.id(), message.attempts());
    }
}
