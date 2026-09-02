package za.co.trademesh.modules.telemetry.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Positioned telemetry only; sensor payloads stay in the telemetry module. */
public interface ShipmentTelemetryCatalog {

    ActualRoute actualRoute(UUID shipmentId);

    record ActualRoute(List<RoutePoint> points, boolean possiblyTruncated) {
        public ActualRoute {
            points = List.copyOf(points);
        }
    }

    record RoutePoint(UUID readingId, Instant recordedAt, Instant receivedAt, double latitude, double longitude) {}
}
