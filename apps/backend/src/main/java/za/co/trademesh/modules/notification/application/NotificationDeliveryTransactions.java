package za.co.trademesh.modules.notification.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.notification.domain.EmailDeliveryAttempt;
import za.co.trademesh.modules.notification.domain.EmailDeliveryAttemptStatus;
import za.co.trademesh.modules.notification.domain.EmailNotification;
import za.co.trademesh.modules.notification.domain.NotificationRepository;

@Component
class NotificationDeliveryTransactions {

    private final NotificationRepository repository;
    private final Clock clock;

    NotificationDeliveryTransactions(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    DeliveryStart begin(UUID notificationId, UUID outboxMessageId, int attemptNumber, String providerKey) {
        EmailNotification notification = repository
                .findNotification(notificationId)
                .orElseThrow(() -> new IllegalStateException("Email notification does not exist"));
        if (notification.terminal()) {
            return new DeliveryStart(notification, null, false);
        }
        var existing = repository.findAttempt(notificationId, outboxMessageId, attemptNumber);
        if (existing.isPresent()) {
            return new DeliveryStart(notification, existing.get(), false);
        }
        EmailDeliveryAttempt attempt = new EmailDeliveryAttempt(
                UUID.randomUUID(),
                notificationId,
                outboxMessageId,
                attemptNumber,
                providerKey,
                EmailDeliveryAttemptStatus.STARTED,
                null,
                null,
                null,
                clock.instant(),
                null);
        if (!repository.saveAttempt(attempt)) {
            EmailDeliveryAttempt concurrent = repository
                    .findAttempt(notificationId, outboxMessageId, attemptNumber)
                    .orElseThrow(() -> new IllegalStateException("Email delivery attempt conflict"));
            return new DeliveryStart(notification, concurrent, false);
        }
        return new DeliveryStart(notification, attempt, true);
    }

    @Transactional
    void sent(UUID notificationId, UUID attemptId, String providerMessageId) {
        repository.markSent(notificationId, attemptId, providerMessageId, clock.instant());
    }

    @Transactional
    void failed(UUID notificationId, UUID attemptId, String failureCode, String failureMessage, boolean finalFailure) {
        repository.markFailed(notificationId, attemptId, failureCode, failureMessage, finalFailure, clock.instant());
    }

    record DeliveryStart(EmailNotification notification, EmailDeliveryAttempt attempt, boolean shouldDeliver) {}
}
