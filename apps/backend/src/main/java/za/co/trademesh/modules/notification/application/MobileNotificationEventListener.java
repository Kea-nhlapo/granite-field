package za.co.trademesh.modules.notification.application;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import za.co.trademesh.modules.access.application.BusinessNotificationRecipients;
import za.co.trademesh.modules.handover.application.HandoverNotificationRecipients;
import za.co.trademesh.modules.handover.events.HandoverEvent;
import za.co.trademesh.modules.payment.application.SandboxWalletService;
import za.co.trademesh.modules.payment.application.SandboxWalletSnapshot;
import za.co.trademesh.modules.payment.events.PaymentEvent;
import za.co.trademesh.modules.supplier.application.SupplierDirectory;
import za.co.trademesh.modules.telemetry.events.TelemetryEvent;
import za.co.trademesh.modules.transport.events.TransportEvent;
import za.co.trademesh.shared.events.PublishedEvent;

@Component
class MobileNotificationEventListener {

    private static final String SHIPMENT_UPDATE = "SHIPMENT_UPDATE";

    private final MobileNotificationRequests notifications;
    private final HandoverNotificationRecipients handoverRecipients;
    private final BusinessNotificationRecipients businessRecipients;
    private final SupplierDirectory suppliers;
    private final SandboxWalletService wallets;
    private final boolean sandboxWalletEnabled;

    MobileNotificationEventListener(
            MobileNotificationRequests notifications,
            HandoverNotificationRecipients handoverRecipients,
            BusinessNotificationRecipients businessRecipients,
            SupplierDirectory suppliers,
            SandboxWalletService wallets,
            @Value("${trademesh.sandbox-wallet.enabled:false}") boolean sandboxWalletEnabled) {
        this.notifications = notifications;
        this.handoverRecipients = handoverRecipients;
        this.businessRecipients = businessRecipients;
        this.suppliers = suppliers;
        this.wallets = wallets;
        this.sandboxWalletEnabled = sandboxWalletEnabled;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCapacityMatch(PublishedEvent<TransportEvent.CapacityMatchCompleted> published) {
        if (published.event().compatibleOfferCount() <= 0) {
            return;
        }
        actor(published)
                .ifPresent(userId -> request(
                        published,
                        userId,
                        NotificationTemplates.CAPACITY_MATCH_FOUND,
                        NotificationTemplates.CAPACITY_MATCH_FOUND_VERSION));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBackhaulMatches(PublishedEvent<TelemetryEvent.BackhaulMatchesFound> published) {
        if (published.event().matchCount() <= 0) {
            return;
        }
        businessRecipients
                .findActiveUserIds(published.event().businessId())
                .forEach(userId -> request(
                        published,
                        userId,
                        NotificationTemplates.CAPACITY_MATCH_FOUND,
                        NotificationTemplates.CAPACITY_MATCH_FOUND_VERSION));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onConfirmationAccepted(PublishedEvent<HandoverEvent.ConfirmationAccepted> published) {
        Optional<UUID> actor = actor(published);
        Optional<HandoverNotificationRecipients.Participants> participants =
                handoverRecipients.find(published.event().challengeId());
        if (actor.isEmpty() || participants.isEmpty()) {
            return;
        }
        other(participants.get(), actor.get())
                .ifPresent(userId -> request(
                        published,
                        userId,
                        NotificationTemplates.HANDOVER_CONFIRMATION_ACCEPTED,
                        NotificationTemplates.HANDOVER_CONFIRMATION_ACCEPTED_VERSION));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onHandoverFinalized(PublishedEvent<HandoverEvent.HandoverFinalized> published) {
        handoverRecipients.find(published.event().challengeId()).ifPresent(participants -> {
            String template = published.event().completedCleanly()
                    ? NotificationTemplates.HANDOVER_FINALIZED_CLEAN
                    : NotificationTemplates.HANDOVER_FINALIZED_DISPUTED;
            int version = published.event().completedCleanly()
                    ? NotificationTemplates.HANDOVER_FINALIZED_CLEAN_VERSION
                    : NotificationTemplates.HANDOVER_FINALIZED_DISPUTED_VERSION;
            request(published, participants.initiatorUserId(), template, version);
            request(published, participants.counterpartyUserId(), template, version);
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEscrowReleased(PublishedEvent<PaymentEvent.Released> published) {
        PaymentEvent.Released event = published.event();
        Optional<UUID> supplierUserId =
                suppliers.find(event.supplierProfileId()).map(SupplierDirectory.SupplierReference::claimedUserId);
        if (supplierUserId.isEmpty()) {
            return;
        }

        if (!sandboxWalletEnabled) {
            request(published, supplierUserId.get(), NotificationTemplates.ESCROW_RELEASED, 1);
            return;
        }

        wallets.settleAndCreditSupplier(
                        published.envelope().eventId(),
                        event.businessId(),
                        event.supplierProfileId(),
                        event.amount(),
                        event.currency())
                .ifPresent(wallet -> request(
                        published,
                        supplierUserId.get(),
                        NotificationTemplates.ESCROW_RELEASED,
                        NotificationTemplates.ESCROW_RELEASED_VERSION,
                        paymentData(event, wallet)));
    }

    private void request(PublishedEvent<?> published, UUID userId, String templateKey, int templateVersion) {
        request(published, userId, templateKey, templateVersion, Map.of());
    }

    private void request(
            PublishedEvent<?> published,
            UUID userId,
            String templateKey,
            int templateVersion,
            Map<String, String> templateData) {
        notifications.requestUser(new MobileNotificationRequests.UserMobileRequest(
                published.event().type(),
                published.envelope().eventId(),
                userId,
                SHIPMENT_UPDATE,
                templateKey,
                templateVersion,
                templateData));
    }

    private static Map<String, String> paymentData(PaymentEvent.Released event, SandboxWalletSnapshot wallet) {
        return Map.of(
                "amount", event.amount().setScale(2).toPlainString(),
                "balance", wallet.availableBalance().setScale(2).toPlainString(),
                "currency", event.currency());
    }

    private static Optional<UUID> actor(PublishedEvent<?> published) {
        return published.envelope().actor().flatMap(MobileNotificationEventListener::uuid);
    }

    private static Optional<UUID> other(HandoverNotificationRecipients.Participants participants, UUID actor) {
        if (actor.equals(participants.initiatorUserId())) {
            return Optional.of(participants.counterpartyUserId());
        }
        if (actor.equals(participants.counterpartyUserId())) {
            return Optional.of(participants.initiatorUserId());
        }
        return Optional.empty();
    }

    private static Optional<UUID> uuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
