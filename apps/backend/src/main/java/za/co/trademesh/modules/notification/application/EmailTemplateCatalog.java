package za.co.trademesh.modules.notification.application;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class EmailTemplateCatalog {

    RenderedEmail render(String templateKey, int templateVersion, Map<String, String> data) {
        if (NotificationTemplates.SUPPLIER_INVITATION.equals(templateKey)
                && templateVersion == NotificationTemplates.SUPPLIER_INVITATION_VERSION) {
            String invitationUrl = required(data, "invitationUrl");
            return new RenderedEmail(
                    "You have a new supplier request",
                    "A buyer has invited you to respond to a request on TradeMesh.\n\n"
                            + "Open the secure request: "
                            + invitationUrl
                            + "\n\nThis link expires and should not be forwarded.");
        }
        if (NotificationTemplates.PROCUREMENT_ORDER_CONFIRMED.equals(templateKey)
                && templateVersion == NotificationTemplates.PROCUREMENT_ORDER_CONFIRMED_VERSION) {
            return new RenderedEmail(
                    "Your order status changed",
                    "An order has been confirmed on TradeMesh. Sign in to view the current details.");
        }
        throw new IllegalArgumentException("Unsupported email template key or version");
    }

    private static String required(Map<String, String> data, String key) {
        String value = data.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required email template data");
        }
        return value.strip();
    }

    record RenderedEmail(String subject, String textBody) {}
}
