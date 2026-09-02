package za.co.trademesh.modules.telemetry.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TelemetryReading(
        UUID id,
        UUID deviceId,
        UUID shipmentId,
        UUID clientEventId,
        String inputFingerprint,
        Instant recordedAt,
        Instant receivedAt,
        Double latitude,
        Double longitude,
        BigDecimal speedKilometresPerHour,
        BigDecimal fuelLitres,
        BigDecimal temperatureCelsius,
        Boolean sealOpen,
        BigDecimal batteryPercent,
        TelemetryNetworkStatus networkStatus,
        Integer networkSignalDbm,
        TelemetryRetentionTier retentionTier) {

    public boolean hasPosition() {
        return latitude != null && longitude != null;
    }
}
