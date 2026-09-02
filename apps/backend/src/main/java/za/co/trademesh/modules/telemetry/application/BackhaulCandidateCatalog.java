package za.co.trademesh.modules.telemetry.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BackhaulCandidateCatalog {

    List<Candidate> find(
            UUID currentShipmentId,
            double currentLatitude,
            double currentLongitude,
            Instant availableFrom,
            Instant availableThrough,
            double radiusMetres,
            int limit);

    record Candidate(
            UUID shipmentId,
            UUID businessId,
            double pickupLatitude,
            double pickupLongitude,
            double destinationLatitude,
            double destinationLongitude,
            Instant windowStart,
            Instant windowEnd,
            long straightLinePickupDistanceMetres,
            BigDecimal averageRating,
            BigDecimal successfulDeliveryRate) {}
}
