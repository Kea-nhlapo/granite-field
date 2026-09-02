package za.co.trademesh.modules.telemetry.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only telemetry boundary exposed to deterministic operational risk rules. */
public interface TelemetryRiskCatalog {

    Optional<RiskReading> findReading(UUID readingId);

    List<RiskReading> findReadingsThrough(UUID shipmentId, Instant from, Instant through, int limit);

    List<OfflineDevice> findOfflineDevices(Instant lastSeenBefore, int limit);

    record OfflineDevice(UUID deviceId, UUID shipmentId, Instant lastSeenAt) {}

    record RiskReading(
            UUID id,
            UUID deviceId,
            UUID shipmentId,
            Instant recordedAt,
            Instant receivedAt,
            Double latitude,
            Double longitude,
            BigDecimal speedKilometresPerHour,
            BigDecimal fuelLitres,
            Boolean sealOpen) {

        public boolean hasPosition() {
            return latitude != null && longitude != null;
        }
    }
}
