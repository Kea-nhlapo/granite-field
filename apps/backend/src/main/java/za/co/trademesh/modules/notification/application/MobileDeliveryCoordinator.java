package za.co.trademesh.modules.notification.application;

import java.util.UUID;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.notification.domain.MobileNotificationStatus;

@Component
class MobileDeliveryCoordinator {

    private final MobileDeliveryTransactions transactions;
    private final MobileDeliveryProvider provider;
    private final MobileTemplateCatalog templates;
    private final MobileNotificationProperties properties;

    MobileDeliveryCoordinator(
            MobileDeliveryTransactions transactions,
            MobileDeliveryProvider provider,
            MobileTemplateCatalog templates,
            MobileNotificationProperties properties) {
        this.transactions = transactions;
        this.provider = provider;
        this.templates = templates;
        this.properties = properties;
    }

    void deliver(UUID notificationId, UUID outboxMessageId, int attemptNumber) throws MobileDeliveryRetryException {
        var start = transactions.begin(notificationId, outboxMessageId, attemptNumber, provider.providerKey());
        if (!start.shouldDeliver()) {
            return;
        }
        var notification = start.notification();
        MobileTemplateCatalog.RenderedMobileTemplate rendered;
        try {
            rendered = templates.render(
                    notification.templateKey(), notification.templateVersion(), notification.templateData());
        } catch (RuntimeException invalidTemplate) {
            transactions.failed(
                    notification.id(),
                    start.attempt().id(),
                    "TEMPLATE_RENDER_FAILED",
                    "The stored mobile template could not be rendered.",
                    true);
            return;
        }
        try {
            var submitted = provider.deliver(new MobileDeliveryProvider.MobileMessage(
                    notification.id(),
                    notification.idempotencyKey(),
                    notification.recipientPhone(),
                    notification.channel(),
                    notification.templateKey(),
                    notification.templateVersion(),
                    rendered.text(),
                    rendered.whatsappParameters(),
                    rendered.whatsappLanguage()));
            if (submitted == null
                    || submitted.providerMessageId() == null
                    || submitted.providerMessageId().isBlank()
                    || (submitted.status() != MobileNotificationStatus.ACCEPTED
                            && submitted.status() != MobileNotificationStatus.QUEUED
                            && submitted.status() != MobileNotificationStatus.SENT)) {
                throw new MobileProviderException(
                        "PROVIDER_RESPONSE_INVALID",
                        "The messaging provider returned an invalid response.",
                        MobileProviderException.FailureKind.SUBMISSION_UNKNOWN);
            }
            transactions.submitted(
                    notification.id(),
                    start.attempt().id(),
                    provider.providerKey(),
                    submitted.providerMessageId().strip(),
                    submitted.status());
        } catch (MobileProviderException failure) {
            handleProviderFailure(notification.id(), start.attempt().id(), attemptNumber, failure);
        } catch (RuntimeException failure) {
            transactions.unknown(
                    notification.id(),
                    start.attempt().id(),
                    provider.providerKey(),
                    "UNEXPECTED_PROVIDER_OUTCOME",
                    "The messaging provider outcome is unknown.");
        }
    }

    private void handleProviderFailure(
            UUID notificationId, UUID attemptId, int attemptNumber, MobileProviderException failure)
            throws MobileDeliveryRetryException {
        if (failure.kind() == MobileProviderException.FailureKind.SUBMISSION_UNKNOWN) {
            transactions.unknown(
                    notificationId, attemptId, provider.providerKey(), failure.code(), failure.getMessage());
            return;
        }
        boolean finalFailure = failure.kind() == MobileProviderException.FailureKind.PERMANENT
                || attemptNumber >= properties.maxDeliveryAttempts();
        transactions.failed(notificationId, attemptId, failure.code(), failure.getMessage(), finalFailure);
        if (!finalFailure) {
            throw new MobileDeliveryRetryException(failure.code(), failure);
        }
    }

    static final class MobileDeliveryRetryException extends Exception {
        MobileDeliveryRetryException(String code, Throwable cause) {
            super("Mobile delivery failed with code " + code, cause);
        }
    }
}
