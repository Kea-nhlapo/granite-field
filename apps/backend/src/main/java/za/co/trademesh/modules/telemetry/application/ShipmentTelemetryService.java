package za.co.trademesh.modules.telemetry.application;

import java.util.Comparator;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.telemetry.domain.TelemetryRepository;

@Service
class ShipmentTelemetryService implements ShipmentTelemetryCatalog {

    private static final int MAX_ROUTE_POINTS = 2_000;

    private final TelemetryRepository telemetry;

    ShipmentTelemetryService(TelemetryRepository telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    @Transactional(readOnly = true)
    public ActualRoute actualRoute(UUID shipmentId) {
        var recent = telemetry.findRecentReadings(shipmentId, MAX_ROUTE_POINTS);
        var points = recent.stream()
                .filter(reading -> reading.latitude() != null && reading.longitude() != null)
                .sorted(Comparator.comparing(reading -> reading.recordedAt()))
                .map(reading -> new RoutePoint(
                        reading.id(),
                        reading.recordedAt(),
                        reading.receivedAt(),
                        reading.latitude(),
                        reading.longitude()))
                .toList();
        return new ActualRoute(points, recent.size() == MAX_ROUTE_POINTS);
    }
}
