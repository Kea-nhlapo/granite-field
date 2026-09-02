package za.co.trademesh.modules.transport.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CapacityOffer(
        UUID id,
        UUID transporterId,
        UUID clientRequestId,
        UUID vehicleId,
        UUID driverAssignmentId,
        List<RoutePoint> routePoints,
        int corridorRadiusMetres,
        Instant departureWindowStart,
        Instant departureWindowEnd,
        Instant expiresAt,
        List<CargoRestriction> restrictions,
        Capacity totalCapacity,
        Capacity remainingCapacity,
        CapacityOfferStatus status,
        UUID createdByUserId,
        Instant createdAt,
        Instant cancelledAt) {

    public CapacityOffer {
        routePoints = List.copyOf(routePoints);
        restrictions = List.copyOf(restrictions);
    }
}
