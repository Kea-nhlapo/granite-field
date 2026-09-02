package za.co.trademesh.modules.notification.application;

import java.util.Map;
import java.util.UUID;

/** Stable command boundary used by feature modules; it contains no email-vendor types. */
public interface NotificationRequests {

    UUID requestEmail(EmailRequest request);

    record EmailRequest(
            String idempotencyKey,
            String recipientEmail,
            UUID recipientUserId,
            String category,
            String templateKey,
            int templateVersion,
            Map<String, String> templateData,
            boolean requiredDelivery) {

        public EmailRequest {
            templateData = templateData == null ? Map.of() : Map.copyOf(templateData);
        }
    }
}
