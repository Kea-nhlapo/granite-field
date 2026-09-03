package za.co.trademesh.modules.notification.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.notification.domain.MobileDeliveryAttempt;
import za.co.trademesh.modules.notification.domain.MobileDeliveryAttemptStatus;
import za.co.trademesh.modules.notification.domain.MobileNotification;
import za.co.trademesh.modules.notification.domain.MobileNotificationRepository;
import za.co.trademesh.modules.notification.domain.MobileNotificationStatus;
import za.co.trademesh.shared.events.outbox.OutboxSubmitter;

@Component
class MobileDeliveryTransactions {

    private final MobileNotificationRepository repository;
    private final OutboxSubmitter outbox;
    private final Clock clock;

    MobileDeliveryTransactions(MobileNotificationRepository repository, OutboxSubmitter outbox, Clock clock) {
        this.repository = repository;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    DeliveryStart begin(UUID notificationId, UUID outboxMessageId, int attemptNumber, String providerKey) {
        MobileNotification notification = repository
                .findNotification(notificationId)
                .orElseThrow(() -> new IllegalStateException("Mobile notification does not exist"));
        if (notification.status() == MobileNotificationStatus.SUBMITTING) {
            MobileDeliveryAttempt interrupted = repository
                    .findLatestStartedAttempt(notificationId)
                    .orElseThrow(() -> new IllegalStateException("Submitting notification has no active attempt"));
            markUnknown(
                    notificationId,
                    interrupted.id(),
                    providerKey,
                    "INTERRUPTED_SUBMISSION",
                    "The messaging submission was interrupted and its outcome is unknown.");
            return new DeliveryStart(notification, interrupted, false);
        }
        if (!notification.status().deliverable()) {
            return new DeliveryStart(notification, null, false);
        }
        var existing = repository.findAttempt(notificationId, outboxMessageId, attemptNumber);
        if (existing.isPresent()) {
            return new DeliveryStart(notification, existing.get(), false);
        }
        MobileDeliveryAttempt attempt = new MobileDeliveryAttempt(
                UUID.randomUUID(),
                notificationId,
                outboxMessageId,
                attemptNumber,
                providerKey,
                MobileDeliveryAttemptStatus.STARTED,
                null,
                null,
                null,
                clock.instant(),
                null);
        if (!repository.saveAttempt(attempt)) {
            MobileDeliveryAttempt concurrent = repository
                    .findAttempt(notificationId, outboxMessageId, attemptNumber)
                    .orElseThrow(() -> new IllegalStateException("Mobile delivery attempt conflict"));
            return new DeliveryStart(notification, concurrent, false);
        }
        if (!repository.markSubmitting(notificationId, clock.instant())) {
            throw new IllegalStateException("Mobile notification submission conflict");
        }
        return new DeliveryStart(notification, attempt, true);
    }

    @Transactional
    void submitted(
            UUID notificationId,
            UUID attemptId,
            String providerKey,
            String providerMessageId,
            MobileNotificationStatus status) {
        repository.markSubmitted(notificationId, attemptId, providerKey, providerMessageId, status, clock.instant());
    }

    @Transactional
    void failed(UUID notificationId, UUID attemptId, String failureCode, String failureMessage, boolean finalFailure) {
        repository.markFailed(notificationId, attemptId, failureCode, failureMessage, finalFailure, clock.instant());
    }

    @Transactional
    void unknown(UUID notificationId, UUID attemptId, String providerKey, String failureCode, String failureMessage) {
        markUnknown(notificationId, attemptId, providerKey, failureCode, failureMessage);
    }

    private void markUnknown(
            UUID notificationId, UUID attemptId, String providerKey, String failureCode, String failureMessage) {
        repository.markSubmissionUnknown(
                notificationId, attemptId, providerKey, failureCode, failureMessage, clock.instant());
        outbox.submit(
                MobileReconciliationRequested.TYPE,
                notificationId.toString(),
                new MobileReconciliationRequested(notificationId),
                MobileReconciliationRequested.SCHEMA_VERSION);
    }

    record DeliveryStart(MobileNotification notification, MobileDeliveryAttempt attempt, boolean shouldDeliver) {}
}
