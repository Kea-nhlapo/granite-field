package za.co.trademesh.modules.routing.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import za.co.trademesh.modules.routing.domain.GeoPoint;
import za.co.trademesh.modules.routing.domain.VehicleLimits;
import za.co.trademesh.modules.shipment.application.ShipmentRouteCatalog;

@Service
public class ShipmentRouteLookupService {

    private final ShipmentRouteCatalog shipments;
    private final RouteProviderGateway providers;
    private final Clock clock;

    public ShipmentRouteLookupService(ShipmentRouteCatalog shipments, RouteProviderGateway providers, Clock clock) {
        this.shipments = shipments;
        this.providers = providers;
        this.clock = clock;
    }

    public RouteView route(UUID businessId, UUID shipmentId) {
        var shipment = shipments
                .findAccessible(required(businessId), required(shipmentId))
                .orElseThrow(RoutingException::calculationNotFound);
        if (shipment.selectedGeometry().size() < 2) {
            throw RoutingException.invalidProviderResult();
        }
        GeoPoint origin = point(shipment.selectedGeometry().getFirst(), "Collection point");
        GeoPoint destination = point(shipment.selectedGeometry().getLast(), "Final destination");
        List<GeoPoint> waypoints = shipment.deliveryStops().stream()
                .map(stop -> point(stop, stop.label()))
                .filter(point -> !same(point, origin) && !same(point, destination))
                .toList();
        RouteProviderGateway.ResolvedRoutes resolved;
        try {
            resolved = providers.resolve(new RouteProvider.ProviderRequest(
                    origin,
                    destination,
                    waypoints,
                    new VehicleLimits(
                            shipment.reservedWeightKg(),
                            new BigDecimal("4.300"),
                            new BigDecimal("2.600"),
                            new BigDecimal("18.750")),
                    List.of(),
                    1));
        } catch (RouteProviderException unavailable) {
            throw RoutingException.providerUnavailable();
        }
        if (resolved.providerResult() == null
                || resolved.providerResult().candidates() == null
                || resolved.providerResult().candidates().isEmpty()) {
            throw RoutingException.invalidProviderResult();
        }
        RouteProvider.ProviderCandidate candidate =
                resolved.providerResult().candidates().getFirst();
        if (candidate.geometry() == null
                || candidate.geometry().size() < 2
                || candidate.distanceMetres() <= 0
                || candidate.durationSeconds() <= 0) {
            throw RoutingException.invalidProviderResult();
        }
        Instant generatedAt = clock.instant();
        return new RouteView(
                shipment.shipmentId(),
                resolved.providerResult().providerName(),
                resolved.providerResult().providerVersion(),
                resolved.fallbackUsed(),
                resolved.fallbackReason(),
                candidate.label(),
                EncodedPolyline.encode(candidate.geometry()),
                candidate.geometry(),
                candidate.distanceMetres(),
                candidate.durationSeconds(),
                generatedAt,
                generatedAt.plusSeconds(candidate.durationSeconds()));
    }

    private static GeoPoint point(ShipmentRouteCatalog.Point point, String fallbackLabel) {
        return new GeoPoint(point.label() == null ? fallbackLabel : point.label(), point.latitude(), point.longitude());
    }

    private static boolean same(GeoPoint first, GeoPoint second) {
        return Math.abs(first.latitude() - second.latitude()) < 0.000001
                && Math.abs(first.longitude() - second.longitude()) < 0.000001;
    }

    private static UUID required(UUID value) {
        if (value == null) {
            throw RoutingException.invalidRequest();
        }
        return value;
    }

    public record RouteView(
            UUID shipmentId,
            String providerName,
            String providerVersion,
            boolean fallbackUsed,
            String fallbackReason,
            String label,
            String encodedPolyline,
            List<GeoPoint> geometry,
            long distanceMetres,
            long durationSeconds,
            Instant generatedAt,
            Instant estimatedArrivalAt) {

        public RouteView {
            geometry = List.copyOf(geometry);
        }
    }
}
