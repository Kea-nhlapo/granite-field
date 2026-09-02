package za.co.trademesh.modules.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.notification.domain.MobileChannel;
import za.co.trademesh.modules.notification.domain.MobileDeliveryAttempt;
import za.co.trademesh.modules.notification.domain.MobileDeliveryAttemptStatus;
import za.co.trademesh.modules.notification.domain.MobileNotification;
import za.co.trademesh.modules.notification.domain.MobileNotificationRepository;
import za.co.trademesh.modules.notification.domain.MobileNotificationStatus;
import za.co.trademesh.modules.notification.domain.NotificationCategory;
import za.co.trademesh.shared.events.outbox.OutboxSubmitter;

class MobileDeliveryTransactionsTest {

    private static final Instant NOW = Instant.parse("2026-09-02T17:00:00Z");

    @Test
    void convertsAnInterruptedSubmissionToReconciliationInsteadOfSendingAgain() {
        MobileNotificationRepository repository = mock(MobileNotificationRepository.class);
        OutboxSubmitter outbox = mock(OutboxSubmitter.class);
        UUID notificationId = UUID.randomUUID();
        UUID outboxMessageId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        MobileNotification notification = notification(notificationId, MobileNotificationStatus.SUBMITTING);
        MobileDeliveryAttempt attempt = new MobileDeliveryAttempt(
                attemptId,
                notificationId,
                outboxMessageId,
                1,
                "infobip",
                MobileDeliveryAttemptStatus.STARTED,
                null,
                null,
                null,
                NOW,
                null);
        when(repository.findNotification(notificationId)).thenReturn(Optional.of(notification));
        when(repository.findLatestStartedAttempt(notificationId)).thenReturn(Optional.of(attempt));
        MobileDeliveryTransactions transactions =
                new MobileDeliveryTransactions(repository, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        var start = transactions.begin(notificationId, outboxMessageId, 2, "infobip");

        assertThat(start.shouldDeliver()).isFalse();
        verify(repository)
                .markSubmissionUnknown(
                        notificationId,
                        attemptId,
                        "infobip",
                        "INTERRUPTED_SUBMISSION",
                        "The messaging submission was interrupted and its outcome is unknown.",
                        NOW);
        verify(outbox)
                .submit(
                        eq(MobileReconciliationRequested.TYPE),
                        eq(notificationId.toString()),
                        any(MobileReconciliationRequested.class),
                        eq(MobileReconciliationRequested.SCHEMA_VERSION));
    }

    private static MobileNotification notification(UUID id, MobileNotificationStatus status) {
        return new MobileNotification(
                id,
                "mobile:test",
                "a".repeat(64),
                UUID.randomUUID(),
                MobileChannel.SMS,
                NotificationCategory.SHIPMENT_UPDATE,
                NotificationTemplates.CAPACITY_MATCH_FOUND,
                1,
                "+27821234567",
                Map.of(),
                status,
                null,
                null,
                NOW,
                null,
                null,
                null,
                null,
                null,
                NOW);
    }
}
