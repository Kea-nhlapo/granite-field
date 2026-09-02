package za.co.trademesh.modules.routing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.trademesh.modules.routing.domain.GeoPoint;
import za.co.trademesh.modules.shipment.application.ShipmentRouteCatalog;

class ShipmentRouteLookupServiceTest {

    @Test
    void calculatesAProviderRouteForAnAccessibleShipmentAndReturnsFrontendReadyEtaAndPolyline() throws Exception {
        UUID businessId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        ShipmentRouteCatalog shipments = mock(ShipmentRouteCatalog.class);
        when(shipments.findAccessible(businessId, shipmentId))
                .thenReturn(Optional.of(new ShipmentRouteCatalog.RouteShipment(
                        shipmentId,
                        new BigDecimal("5000.000"),
                        List.of(
                                new ShipmentRouteCatalog.Point(null, -26.2041, 28.0473),
                                new ShipmentRouteCatalog.Point(null, -25.7479, 28.2293)),
                        List.of(new ShipmentRouteCatalog.Point("Midrand", -25.9992, 28.1263)))));
        RouteProviderGateway providers = request ->
                new RouteProviderGateway.ResolvedRoutes(result(request.origin(), request.destination()), false, null);
        Instant now = Instant.parse("2026-09-02T12:00:00Z");
        var service = new ShipmentRouteLookupService(shipments, providers, Clock.fixed(now, ZoneOffset.UTC));

        var route = service.route(businessId, shipmentId);

        assertThat(route.providerName()).isEqualTo("google-directions");
        assertThat(route.encodedPolyline()).isNotBlank();
        assertThat(route.distanceMetres()).isEqualTo(61_000);
        assertThat(route.estimatedArrivalAt()).isEqualTo(now.plusSeconds(3_100));
    }

    private static RouteProvider.ProviderResult result(GeoPoint origin, GeoPoint destination) {
        var segment = new RouteProvider.ProviderSegment(
                0, "Johannesburg", "Pretoria", List.of(origin, destination), 61_000, 3_100, new BigDecimal("0.00"));
        return new RouteProvider.ProviderResult(
                "google-directions",
                "directions-v1",
                List.of(new RouteProvider.ProviderCandidate(
                        "google-0",
                        "N1",
                        List.of(origin, destination),
                        61_000,
                        3_100,
                        new BigDecimal("0.00"),
                        List.of(segment))));
    }
}
