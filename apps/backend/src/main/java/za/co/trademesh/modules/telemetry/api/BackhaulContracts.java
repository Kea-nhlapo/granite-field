package za.co.trademesh.modules.telemetry.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.telemetry.application.BackhaulMatchingService;

final class BackhaulContracts {

    private BackhaulContracts() {}

    record BackhaulMatchesResponse(List<BackhaulMatchResponse> matches) {
        static BackhaulMatchesResponse from(List<BackhaulMatchingService.BackhaulMatch> matches) {
            return new BackhaulMatchesResponse(
                    matches.stream().map(BackhaulMatchResponse::from).toList());
        }
    }

    record BackhaulMatchResponse(
            UUID shipmentId,
            UUID businessId,
            PointResponse pickup,
            PointResponse destination,
            Instant windowStart,
            Instant windowEnd,
            long pickupDistanceMetres,
            long pickupDurationSeconds,
            boolean roadDistanceMeasured,
            BigDecimal averageRating,
            BigDecimal successfulDeliveryRate,
            BigDecimal trustScore,
            BigDecimal score) {

        static BackhaulMatchResponse from(BackhaulMatchingService.BackhaulMatch match) {
            return new BackhaulMatchResponse(
                    match.shipmentId(),
                    match.businessId(),
                    new PointResponse(match.pickupLatitude(), match.pickupLongitude()),
                    new PointResponse(match.destinationLatitude(), match.destinationLongitude()),
                    match.windowStart(),
                    match.windowEnd(),
                    match.pickupDistanceMetres(),
                    match.pickupDurationSeconds(),
                    match.roadDistanceMeasured(),
                    match.averageRating(),
                    match.successfulDeliveryRate(),
                    match.trustScore(),
                    match.score());
        }
    }

    record PointResponse(double latitude, double longitude) {}
}
