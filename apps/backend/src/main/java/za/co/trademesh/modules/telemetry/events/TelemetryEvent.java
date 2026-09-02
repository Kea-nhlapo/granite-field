package za.co.trademesh.modules.telemetry.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import za.co.trademesh.modules.telemetry.domain.TelemetryNetworkStatus;
import za.co.trademesh.shared.events.DomainEvent;

public sealed interface TelemetryEvent extends DomainEvent
        permits TelemetryEvent.ReadingAccepted, TelemetryEvent.BackhaulMatchesFound {

    @Override
    default int schemaVersion() {
        return 1;
    }

    record ReadingAccepted(
            UUID readingId,
            UUID shipmentId,
            UUID deviceId,
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
            Integer networkSignalDbm)
            implements TelemetryEvent {

        @Override
        public String type() {
            return "TELEMETRY_READING_ACCEPTED";
        }
    }

    record BackhaulMatchesFound(
            UUID shipmentId,
            UUID businessId,
            UUID topCandidateShipmentId,
            int matchCount,
            long pickupDistanceMetres,
            BigDecimal trustScore)
            implements TelemetryEvent {

        @Override
        public String type() {
            return "BACKHAUL_MATCH_FOUND";
        }
    }
}
