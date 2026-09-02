package za.co.trademesh.modules.notification.infrastructure;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.notifications.mobile.providers.twilio")
public record TwilioMessagingProperties(
        URI baseUrl, String accountSid, String authToken, String smsFrom, String whatsAppFrom) {

    public TwilioMessagingProperties {
        baseUrl = baseUrl == null ? URI.create("https://api.twilio.com") : baseUrl;
        if (!baseUrl.isAbsolute()) {
            throw new IllegalArgumentException("Twilio Messaging base URL must be absolute");
        }
        accountSid = value(accountSid);
        authToken = value(authToken);
        smsFrom = value(smsFrom);
        whatsAppFrom = value(whatsAppFrom);
    }

    private static String value(String value) {
        return value == null ? "" : value.strip();
    }
}
