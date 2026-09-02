package za.co.trademesh.modules.notification.application;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.notifications.mobile.providers.infobip")
public record InfobipNotificationProperties(
        String baseUrl,
        String apiKey,
        String smsSender,
        String whatsappSender,
        String webhookHmacSecret,
        Map<String, String> whatsappTemplates,
        Duration connectTimeout,
        Duration readTimeout) {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(15);

    public InfobipNotificationProperties {
        baseUrl = clean(baseUrl);
        apiKey = clean(apiKey);
        smsSender = clean(smsSender);
        whatsappSender = clean(whatsappSender);
        webhookHmacSecret = clean(webhookHmacSecret);
        whatsappTemplates = whatsappTemplates == null ? Map.of() : Map.copyOf(whatsappTemplates);
        connectTimeout = positive(connectTimeout, DEFAULT_CONNECT_TIMEOUT);
        readTimeout = positive(readTimeout, DEFAULT_READ_TIMEOUT);
    }

    public String whatsappTemplate(String key, int version) {
        return clean(whatsappTemplates.get(key + ".v" + version));
    }

    @Override
    public String toString() {
        return "InfobipNotificationProperties[baseUrl=<redacted>, apiKey=<redacted>, smsSender=<redacted>, "
                + "whatsappSender=<redacted>, webhookHmacSecret=<redacted>, whatsappTemplates=<redacted>]";
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
