package za.co.trademesh.modules.notification.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.notification.domain.MobileChannel;
import za.co.trademesh.modules.notification.domain.MobileDeliveryAttempt;
import za.co.trademesh.modules.notification.domain.MobileDeliveryAttemptStatus;
import za.co.trademesh.modules.notification.domain.MobileNotification;
import za.co.trademesh.modules.notification.domain.MobileNotificationStatus;
import za.co.trademesh.modules.notification.domain.NotificationCategory;

class MobileDeliveryCoordinatorTest {

    @Test
    void permanentlyFailsAnInvalidStoredTemplateWithoutCallingTheProvider() throws Exception {
        MobileDeliveryTransactions transactions = mock(MobileDeliveryTransactions.class);
        MobileDeliveryProvider provider = mock(MobileDeliveryProvider.class);
        MobileTemplateCatalog templates = mock(MobileTemplateCatalog.class);
        UUID notificationId = UUID.randomUUID();
        UUID outboxMessageId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-02T17:00:00Z");
        MobileNotification notification = new MobileNotification(
                notificationId,
                "mobile:test",
                "a".repeat(64),
                UUID.randomUUID(),
                MobileChannel.SMS,
                NotificationCategory.SHIPMENT_UPDATE,
                NotificationTemplates.CAPACITY_MATCH_FOUND,
                1,
                "+27821234567",
                Map.of(),
                MobileNotificationStatus.PENDING,
                null,
                null,
                now,
                null,
                null,
                null,
                null,
                null,
                now);
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
                now,
                null);
        when(provider.providerKey()).thenReturn("infobip");
        when(transactions.begin(notificationId, outboxMessageId, 1, "infobip"))
                .thenReturn(new MobileDeliveryTransactions.DeliveryStart(notification, attempt, true));
        when(templates.render(notification.templateKey(), notification.templateVersion(), notification.templateData()))
                .thenThrow(new IllegalArgumentException("corrupt template"));
        var coordinator = new MobileDeliveryCoordinator(
                transactions, provider, templates, new MobileNotificationProperties("infobip", 3));

        coordinator.deliver(notificationId, outboxMessageId, 1);

        verify(transactions)
                .failed(
                        notificationId,
                        attemptId,
                        "TEMPLATE_RENDER_FAILED",
                        "The stored mobile template could not be rendered.",
                        true);
        verify(provider, never()).deliver(any());
    }
}
