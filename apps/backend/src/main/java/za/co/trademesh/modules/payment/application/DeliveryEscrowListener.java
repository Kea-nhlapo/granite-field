package za.co.trademesh.modules.payment.application;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.delivery.events.DeliveryEvent;
import za.co.trademesh.shared.events.PublishedEvent;

@Component
class DeliveryEscrowListener {

    private final EscrowService escrow;

    DeliveryEscrowListener(EscrowService escrow) {
        this.escrow = escrow;
    }

    @EventListener
    public void onAccepted(PublishedEvent<DeliveryEvent.DeliveryAccepted> published) {
        escrow.prepareLock(published.event());
    }
}
