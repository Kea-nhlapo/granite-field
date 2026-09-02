package za.co.trademesh.modules.notification.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import za.co.trademesh.modules.notification.domain.MobileChannel;
import za.co.trademesh.modules.notification.domain.MobileNotificationStatus;

public interface MobileDeliveryProvider {

    String providerKey();

    SubmissionResult deliver(MobileMessage message);

    default Optional<ReconciliationResult> reconcile(UUID notificationId) {
        return Optional.empty();
    }

    record MobileMessage(
            UUID notificationId,
            String idempotencyKey,
            String recipientPhone,
            MobileChannel channel,
            String templateKey,
            int templateVersion,
            String text,
            List<String> whatsappParameters,
            String whatsappLanguage) {

        public MobileMessage {
            whatsappParameters = List.copyOf(whatsappParameters);
        }

        @Override
        public String toString() {
            return "MobileMessage[notificationId=" + notificationId + ", idempotencyKey=" + idempotencyKey
                    + ", recipientPhone=<redacted>, channel=" + channel + ", templateKey=" + templateKey
                    + ", templateVersion=" + templateVersion
                    + ", text=<redacted>, whatsappParameters=<redacted>, whatsappLanguage=" + whatsappLanguage + "]";
        }
    }

    record SubmissionResult(String providerMessageId, MobileNotificationStatus status) {}

    record ReconciliationResult(
            String providerMessageId, String providerStatus, MobileNotificationStatus status, Instant observedAt) {}
}
