package za.co.trademesh.modules.notification.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.notifications.mobile")
public record MobileNotificationProperties(
        String provider, String baseUrl, String accountSid, String authToken, String smsFrom, String whatsAppFrom) {

    public MobileNotificationProperties {
        provider = value(provider, "local").toLowerCase(java.util.Locale.ROOT);
        baseUrl = value(baseUrl, "https://api.twilio.com");
        accountSid = value(accountSid, "");
        authToken = value(authToken, "");
        smsFrom = value(smsFrom, "");
        whatsAppFrom = value(whatsAppFrom, "");
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
