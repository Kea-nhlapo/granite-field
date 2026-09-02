package za.co.trademesh.modules.notification.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class EmailDeliveryCoordinator {

    private final NotificationDeliveryTransactions transactions;
    private final EmailDeliveryProvider provider;
    private final EmailTemplateCatalog templates;
    private final NotificationEmailProperties properties;

    EmailDeliveryCoordinator(
            NotificationDeliveryTransactions transactions,
            EmailDeliveryProvider provider,
            EmailTemplateCatalog templates,
            NotificationEmailProperties properties) {
        this.transactions = transactions;
        this.provider = provider;
        this.templates = templates;
        this.properties = properties;
    }

    void deliver(UUID notificationId, UUID outboxMessageId, int attemptNumber) throws EmailDeliveryRetryException {
        var start = transactions.begin(notificationId, outboxMessageId, attemptNumber, provider.providerKey());
        if (!start.shouldDeliver()) {
            return;
        }

        var notification = start.notification();
        var rendered = templates.render(
                notification.templateKey(), notification.templateVersion(), notification.templateData());
        try {
            var delivered = provider.deliver(new EmailDeliveryProvider.EmailMessage(
                    notification.id().toString(),
                    properties.fromAddress(),
                    notification.recipientEmail(),
                    rendered.subject(),
                    rendered.textBody()));
            if (delivered.providerMessageId() == null
                    || delivered.providerMessageId().isBlank()) {
                throw new EmailProviderException(
                        "PROVIDER_RESPONSE_INVALID", "The email provider returned no message ID.", true);
            }
            transactions.sent(
                    notification.id(),
                    start.attempt().id(),
                    delivered.providerMessageId().strip());
        } catch (EmailProviderException failure) {
            boolean finalFailure = !failure.retryable() || attemptNumber >= properties.maxDeliveryAttempts();
            transactions.failed(
                    notification.id(), start.attempt().id(), failure.code(), failure.getMessage(), finalFailure);
            if (!finalFailure) {
                throw new EmailDeliveryRetryException(failure.code(), failure);
            }
        } catch (RuntimeException failure) {
            boolean finalFailure = attemptNumber >= properties.maxDeliveryAttempts();
            transactions.failed(
                    notification.id(),
                    start.attempt().id(),
                    "UNEXPECTED_PROVIDER_ERROR",
                    "The email provider failed unexpectedly.",
                    finalFailure);
            if (!finalFailure) {
                throw new EmailDeliveryRetryException("UNEXPECTED_PROVIDER_ERROR", failure);
            }
        }
    }

    static final class EmailDeliveryRetryException extends Exception {
        EmailDeliveryRetryException(String code, Throwable cause) {
            super("Email delivery failed with code " + code, cause);
        }
    }
}
