package za.co.trademesh.modules.telemetry.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.shipment.application.ShipmentAccessCatalog;
import za.co.trademesh.modules.telemetry.domain.TelemetryRepository;
import za.co.trademesh.modules.telemetry.events.TelemetryEvent;
import za.co.trademesh.shared.events.DomainEvents;

@Service
public class BackhaulMatchingService {

    private static final BigDecimal FIVE = new BigDecimal("5");

    private final ShipmentAccessCatalog shipments;
    private final TelemetryRepository telemetry;
    private final BackhaulCandidateCatalog candidates;
    private final BackhaulDistanceClient distances;
    private final TrackingProperties properties;
    private final DomainEvents events;
    private final Clock clock;

    public BackhaulMatchingService(
            ShipmentAccessCatalog shipments,
            TelemetryRepository telemetry,
            BackhaulCandidateCatalog candidates,
            BackhaulDistanceClient distances,
            TrackingProperties properties,
            DomainEvents events,
            Clock clock) {
        this.shipments = shipments;
        this.telemetry = telemetry;
        this.candidates = candidates;
        this.distances = distances;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public List<BackhaulMatch> find(UUID businessId, UUID shipmentId) {
        UUID business = required(businessId);
        UUID shipment = required(shipmentId);
        shipments.findOwned(business, shipment).orElseThrow(TelemetryException::shipmentNotFound);
        var position = telemetry.findLivePosition(shipment).orElseThrow(TelemetryException::livePositionNotFound);
        Instant now = clock.instant();
        List<BackhaulCandidateCatalog.Candidate> found = candidates.find(
                shipment,
                position.latitude(),
                position.longitude(),
                now,
                now.plus(properties.backhaulTimeWindow()),
                properties.backhaulRadiusMetres(),
                properties.backhaulCandidateLimit());
        Map<UUID, BackhaulDistanceClient.Distance> measured;
        try {
            measured = distances.distances(
                    position.latitude(),
                    position.longitude(),
                    found.stream()
                            .map(candidate -> new BackhaulDistanceClient.Pickup(
                                    candidate.shipmentId(), candidate.pickupLatitude(), candidate.pickupLongitude()))
                            .toList());
        } catch (RuntimeException providerFailure) {
            measured = Map.of();
        }
        Map<UUID, BackhaulDistanceClient.Distance> roadDistances = measured;
        List<BackhaulMatch> matches = found.stream()
                .map(candidate -> score(candidate, roadDistances.get(candidate.shipmentId())))
                .sorted(Comparator.comparing(BackhaulMatch::score)
                        .reversed()
                        .thenComparing(BackhaulMatch::pickupDistanceMetres)
                        .thenComparing(BackhaulMatch::shipmentId))
                .toList();
        if (!matches.isEmpty()) {
            BackhaulMatch best = matches.getFirst();
            events.publish(new TelemetryEvent.BackhaulMatchesFound(
                    shipment,
                    business,
                    best.shipmentId(),
                    matches.size(),
                    best.pickupDistanceMetres(),
                    best.trustScore()));
        }
        return matches;
    }

    private BackhaulMatch score(
            BackhaulCandidateCatalog.Candidate candidate, BackhaulDistanceClient.Distance measured) {
        long distance = measured == null ? candidate.straightLinePickupDistanceMetres() : measured.metres();
        long duration = measured == null ? Math.max(60, Math.round(distance / 12.5)) : measured.durationSeconds();
        BigDecimal distanceScore = BigDecimal.ONE
                .subtract(BigDecimal.valueOf(distance)
                        .divide(BigDecimal.valueOf(properties.backhaulRadiusMetres()), 8, RoundingMode.HALF_UP))
                .max(BigDecimal.ZERO);
        BigDecimal trustScore = trust(candidate.averageRating(), candidate.successfulDeliveryRate());
        BigDecimal weightTotal =
                BigDecimal.valueOf(properties.backhaulDistanceWeight() + properties.backhaulTrustWeight());
        BigDecimal score = distanceScore
                .multiply(BigDecimal.valueOf(properties.backhaulDistanceWeight()))
                .add(trustScore.multiply(BigDecimal.valueOf(properties.backhaulTrustWeight())))
                .divide(weightTotal, 6, RoundingMode.HALF_UP);
        return new BackhaulMatch(
                candidate.shipmentId(),
                candidate.businessId(),
                candidate.pickupLatitude(),
                candidate.pickupLongitude(),
                candidate.destinationLatitude(),
                candidate.destinationLongitude(),
                candidate.windowStart(),
                candidate.windowEnd(),
                distance,
                duration,
                measured != null,
                candidate.averageRating(),
                candidate.successfulDeliveryRate(),
                trustScore.setScale(6, RoundingMode.HALF_UP),
                score);
    }

    private static BigDecimal trust(BigDecimal rating, BigDecimal successRate) {
        BigDecimal ratingScore = rating == null ? new BigDecimal("0.5") : rating.divide(FIVE, 8, RoundingMode.HALF_UP);
        BigDecimal deliveryScore = successRate == null ? new BigDecimal("0.5") : successRate;
        return ratingScore.add(deliveryScore).divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
    }

    private static UUID required(UUID value) {
        if (value == null) {
            throw TelemetryException.invalidRequest();
        }
        return value;
    }

    public record BackhaulMatch(
            UUID shipmentId,
            UUID businessId,
            double pickupLatitude,
            double pickupLongitude,
            double destinationLatitude,
            double destinationLongitude,
            Instant windowStart,
            Instant windowEnd,
            long pickupDistanceMetres,
            long pickupDurationSeconds,
            boolean roadDistanceMeasured,
            BigDecimal averageRating,
            BigDecimal successfulDeliveryRate,
            BigDecimal trustScore,
            BigDecimal score) {}
}
