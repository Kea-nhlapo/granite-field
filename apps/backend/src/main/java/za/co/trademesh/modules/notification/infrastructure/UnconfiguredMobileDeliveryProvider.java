package za.co.trademesh.modules.notification.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.notification.application.MobileDeliveryProvider;

/** Fallback mobile provider: the application starts, sending a message fails loudly. */
@Component
@ConditionalOnProperty(
        prefix = "trademesh.notifications.mobile",
        name = "provider",
        havingValue = "unconfigured",
        matchIfMissing = true)
class UnconfiguredMobileDeliveryProvider implements MobileDeliveryProvider {

    @Override
    public String providerKey() {
        return "unconfigured";
    }

    @Override
    public SubmissionResult deliver(MobileMessage message) {
        throw new IllegalStateException(
                "No mobile notification provider is configured; set trademesh.notifications.mobile.provider");
    }
}
