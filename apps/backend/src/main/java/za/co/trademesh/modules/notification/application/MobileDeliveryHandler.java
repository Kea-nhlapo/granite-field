package za.co.trademesh.modules.notification.application;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import za.co.trademesh.shared.events.outbox.OutboxHandler;
import za.co.trademesh.shared.events.outbox.OutboxMessage;

@Component
class MobileDeliveryHandler implements OutboxHandler {

    private final ObjectMapper objectMapper;
    private final NotificationDataProtector dataProtector;
    private final MobileDeliveryProvider provider;

    MobileDeliveryHandler(
            ObjectMapper objectMapper, NotificationDataProtector dataProtector, MobileDeliveryProvider provider) {
        this.objectMapper = objectMapper;
        this.dataProtector = dataProtector;
        this.provider = provider;
    }

    @Override
    public String type() {
        return MobileDeliveryRequested.TYPE;
    }

    @Override
    public void handle(OutboxMessage message) {
        MobileDeliveryRequested requested = objectMapper.readValue(message.payload(), MobileDeliveryRequested.class);
        provider.deliver(new MobileDeliveryProvider.MobileMessage(
                requested.idempotencyKey(),
                dataProtector.unprotect(requested.protectedRecipient()),
                requested.channel(),
                dataProtector.unprotect(requested.protectedMessage())));
    }
}
