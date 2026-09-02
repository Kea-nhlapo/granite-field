package za.co.trademesh.modules.delivery.application;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.delivery.search")
public record DeliverySearchProperties(
        String speechProvider,
        String speechEndpoint,
        String speechApiKey,
        List<String> languageCodes,
        String distanceProvider,
        String distanceEndpoint,
        String mapsApiKey,
        long maximumAudioBytes,
        int supplierLimit) {

    public DeliverySearchProperties {
        speechProvider = normalized(speechProvider, "local");
        speechEndpoint = text(speechEndpoint);
        speechApiKey = text(speechApiKey);
        languageCodes = languageCodes == null || languageCodes.isEmpty()
                ? List.of("en-ZA", "zu-ZA", "xh-ZA", "af-ZA")
                : languageCodes.stream()
                        .map(String::strip)
                        .filter(value -> !value.isBlank())
                        .toList();
        distanceProvider = normalized(distanceProvider, "local");
        distanceEndpoint = text(distanceEndpoint);
        mapsApiKey = text(mapsApiKey);
        if (maximumAudioBytes <= 0) {
            maximumAudioBytes = 2_097_152;
        }
        if (supplierLimit < 1 || supplierLimit > 50) {
            supplierLimit = 10;
        }
    }

    private static String normalized(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip().toLowerCase(Locale.ROOT);
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }
}
