package za.co.trademesh.modules.shipment.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Shipment route data exposed without leaking the shipment persistence model. */
public interface ShipmentRouteCatalog {

    Optional<RouteShipment> findAccessible(UUID businessId, UUID shipmentId);

    record RouteShipment(
            UUID shipmentId, BigDecimal reservedWeightKg, List<Point> selectedGeometry, List<Point> deliveryStops) {

        public RouteShipment {
            selectedGeometry = List.copyOf(selectedGeometry);
            deliveryStops = List.copyOf(deliveryStops);
        }
    }

    record Point(String label, double latitude, double longitude) {}
}
