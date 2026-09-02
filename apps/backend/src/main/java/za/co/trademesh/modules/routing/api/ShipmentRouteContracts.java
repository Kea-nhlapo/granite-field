package za.co.trademesh.modules.routing.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.routing.application.ShipmentRouteLookupService;

final class ShipmentRouteContracts {

    private ShipmentRouteContracts() {}

    record RouteResponse(
            UUID shipmentId,
            String providerName,
            String providerVersion,
            boolean fallbackUsed,
            String fallbackReason,
            String label,
            String encodedPolyline,
            List<RoutingContracts.PointResponse> geometry,
            long distanceMetres,
            long durationSeconds,
            Instant generatedAt,
            Instant estimatedArrivalAt) {

        static RouteResponse from(ShipmentRouteLookupService.RouteView route) {
            return new RouteResponse(
                    route.shipmentId(),
                    route.providerName(),
                    route.providerVersion(),
                    route.fallbackUsed(),
                    route.fallbackReason(),
                    route.label(),
                    route.encodedPolyline(),
                    route.geometry().stream()
                            .map(RoutingContracts.PointResponse::geometry)
                            .toList(),
                    route.distanceMetres(),
                    route.durationSeconds(),
                    route.generatedAt(),
                    route.estimatedArrivalAt());
        }
    }
}
