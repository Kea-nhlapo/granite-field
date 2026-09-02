package za.co.trademesh.modules.telemetry.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TelemetryLivePosition(
        UUID shipmentId,
        UUID deviceId,
        UUID readingId,
        Instant recordedAt,
        Instant receivedAt,
        double latitude,
        double longitude,
        BigDecimal speedKilometresPerHour,
        BigDecimal batteryPercent,
        TelemetryNetworkStatus networkStatus,
        Integer networkSignalDbm) {}
