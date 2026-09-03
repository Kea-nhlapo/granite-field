package za.co.trademesh.modules.notification.application;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import za.co.trademesh.shared.events.outbox.OutboxHandler;
import za.co.trademesh.shared.events.outbox.OutboxMessage;

@Component
class MobileReconciliationHandler implements OutboxHandler {

    private final ObjectMapper objectMapper;
    private final MobileReconciliationCoordinator coordinator;

    MobileReconciliationHandler(ObjectMapper objectMapper, MobileReconciliationCoordinator coordinator) {
        this.objectMapper = objectMapper;
        this.coordinator = coordinator;
    }

    @Override
    public String type() {
        return MobileReconciliationRequested.TYPE;
    }

    @Override
    public void handle(OutboxMessage message) throws Exception {
        MobileReconciliationRequested requested =
                objectMapper.readValue(message.payload(), MobileReconciliationRequested.class);
        coordinator.reconcile(requested.notificationId());
    }
}
