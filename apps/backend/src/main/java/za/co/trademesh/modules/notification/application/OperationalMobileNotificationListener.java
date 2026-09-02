package za.co.trademesh.modules.notification.application;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import za.co.trademesh.modules.access.application.VerifiedPhoneCatalog;
import za.co.trademesh.modules.handover.events.HandoverEvent;
import za.co.trademesh.modules.payment.events.PaymentEvent;
import za.co.trademesh.modules.telemetry.events.TelemetryEvent;
import za.co.trademesh.shared.events.PublishedEvent;

@Component
class OperationalMobileNotificationListener {

    private final VerifiedPhoneCatalog phones;
    private final MobileNotificationRequests notifications;

    OperationalMobileNotificationListener(VerifiedPhoneCatalog phones, MobileNotificationRequests notifications) {
        this.phones = phones;
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void enqueue(PublishedEvent<?> published) {
        if (published.event() instanceof TelemetryEvent.BackhaulMatchesFound found) {
            send(
                    found.businessId(),
                    "backhaul-match:" + found.shipmentId(),
                    OperationalMobileNotificationTemplates.backhaul(
                            found.shipmentId(), found.matchCount(), found.pickupDistanceMetres(), found.trustScore()));
        } else if (published.event() instanceof HandoverEvent.HandoverFinalized finalized
                && "DELIVERY".equals(finalized.handoverType())) {
            send(
                    finalized.businessId(),
                    "delivery-scan:" + finalized.challengeId(),
                    OperationalMobileNotificationTemplates.deliveryScan(finalized.shipmentId(), finalized.outcome()));
        } else if (published.event() instanceof PaymentEvent.Released released) {
            send(
                    released.businessId(),
                    "escrow-released:" + released.escrowId(),
                    OperationalMobileNotificationTemplates.escrowReleased(
                            released.shipmentId(), released.amount(), released.currency()));
        }
    }

    private void send(UUID businessId, String idempotencyKey, String message) {
        phones.findPrimaryForBusiness(businessId)
                .ifPresent(phone -> notifications.sendWhatsApp(idempotencyKey, phone, message));
    }
}
