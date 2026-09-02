package za.co.trademesh.modules.evidence.application;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EvidenceMetadata {

    private EvidenceMetadata() {}

    public static Map<String, String> of(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Evidence metadata requires key/value pairs");
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            String key = String.valueOf(keyValues[index]);
            Object value = keyValues[index + 1];
            if (value != null) {
                metadata.put(key, value(value));
            }
        }
        return Map.copyOf(metadata);
    }

    private static String value(Object value) {
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        return String.valueOf(value);
    }
}
