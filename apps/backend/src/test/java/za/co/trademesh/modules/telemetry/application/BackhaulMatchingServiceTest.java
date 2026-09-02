package za.co.trademesh.modules.telemetry.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.shipment.application.ShipmentAccessCatalog;
import za.co.trademesh.modules.telemetry.domain.TelemetryLivePosition;
import za.co.trademesh.modules.telemetry.domain.TelemetryRepository;

class BackhaulMatchingServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    @Test
    void combinesMeasuredPickupDetourWithPublicTrustAndReturnsAnExplainableRanking() {
        UUID businessId = UUID.randomUUID();
        UUID currentShipment = UUID.randomUUID();
        UUID nearShipment = UUID.randomUUID();
        UUID trustedShipment = UUID.randomUUID();
        ShipmentAccessCatalog shipments = mock(ShipmentAccessCatalog.class);
        when(shipments.findOwned(businessId, currentShipment))
                .thenReturn(Optional.of(new ShipmentAccessCatalog.ShipmentAccess(currentShipment, true)));
        TelemetryRepository telemetry = mock(TelemetryRepository.class);
        when(telemetry.findLivePosition(currentShipment)).thenReturn(Optional.of(position(currentShipment)));
        BackhaulCandidateCatalog candidates = (ignored, latitude, longitude, from, through, radius, limit) -> List.of(
                candidate(nearShipment, 2_000, "3.5", "0.80"), candidate(trustedShipment, 5_000, "4.9", "0.99"));
        BackhaulDistanceClient distances = (latitude, longitude, pickups) -> Map.of(
                nearShipment, new BackhaulDistanceClient.Distance(2_500, 300),
                trustedShipment, new BackhaulDistanceClient.Distance(6_000, 600));
        var service = new BackhaulMatchingService(
                shipments, telemetry, candidates, distances, properties(), Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.find(businessId, currentShipment);

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(BackhaulMatchingService.BackhaulMatch::shipmentId)
                .containsExactly(nearShipment, trustedShipment);
        assertThat(result.getFirst().roadDistanceMeasured()).isTrue();
        assertThat(result.getFirst().pickupDistanceMetres()).isEqualTo(2_500);
        assertThat(result.getFirst().score()).isBetween(new BigDecimal("0.000000"), new BigDecimal("1.000000"));
    }

    @Test
    void fallsBackToPostgisDistanceWhenTheRoadProviderIsUnavailable() {
        UUID businessId = UUID.randomUUID();
        UUID currentShipment = UUID.randomUUID();
        UUID candidateShipment = UUID.randomUUID();
        ShipmentAccessCatalog shipments = mock(ShipmentAccessCatalog.class);
        when(shipments.findOwned(businessId, currentShipment))
                .thenReturn(Optional.of(new ShipmentAccessCatalog.ShipmentAccess(currentShipment, true)));
        TelemetryRepository telemetry = mock(TelemetryRepository.class);
        when(telemetry.findLivePosition(currentShipment)).thenReturn(Optional.of(position(currentShipment)));
        BackhaulCandidateCatalog candidates = (ignored, latitude, longitude, from, through, radius, limit) ->
                List.of(candidate(candidateShipment, 1_900, null, null));
        BackhaulDistanceClient distances = (latitude, longitude, pickups) -> {
            throw new IllegalStateException("distance provider unavailable");
        };
        var service = new BackhaulMatchingService(
                shipments, telemetry, candidates, distances, properties(), Clock.fixed(NOW, ZoneOffset.UTC));

        var match = service.find(businessId, currentShipment).getFirst();

        assertThat(match.roadDistanceMeasured()).isFalse();
        assertThat(match.pickupDistanceMetres()).isEqualTo(1_900);
        assertThat(match.trustScore()).isEqualByComparingTo("0.500000");
    }

    private static TelemetryLivePosition position(UUID shipmentId) {
        return new TelemetryLivePosition(
                shipmentId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                NOW.minusSeconds(1),
                NOW,
                -26.2041,
                28.0473,
                null,
                null,
                null,
                null);
    }

    private static BackhaulCandidateCatalog.Candidate candidate(
            UUID shipmentId, long distance, String rating, String successRate) {
        return new BackhaulCandidateCatalog.Candidate(
                shipmentId,
                UUID.randomUUID(),
                -26.19,
                28.06,
                -25.75,
                28.23,
                NOW.plus(Duration.ofHours(1)),
                NOW.plus(Duration.ofHours(4)),
                distance,
                rating == null ? null : new BigDecimal(rating),
                successRate == null ? null : new BigDecimal(successRate));
    }

    private static TrackingProperties properties() {
        return new TrackingProperties(Duration.ofMinutes(30), 30_000, Duration.ofHours(6), 20, 0.70, 0.30);
    }
}
