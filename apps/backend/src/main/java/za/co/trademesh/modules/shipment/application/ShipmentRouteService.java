package za.co.trademesh.modules.shipment.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.shipment.domain.Shipment;
import za.co.trademesh.modules.shipment.domain.ShipmentRepository;

@Service
class ShipmentRouteService implements ShipmentRouteCatalog {

    private final ShipmentRepository shipments;

    ShipmentRouteService(ShipmentRepository shipments) {
        this.shipments = shipments;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RouteShipment> findAccessible(UUID businessId, UUID shipmentId) {
        return shipments.findByParticipantBusinessId(businessId, shipmentId).map(ShipmentRouteService::snapshot);
    }

    private static RouteShipment snapshot(Shipment shipment) {
        var assignment = shipment.currentAssignment();
        return new RouteShipment(
                shipment.id(),
                shipment.reservedWeightKg(),
                assignment.routeGeometry().stream()
                        .map(point -> new Point(null, point.latitude(), point.longitude()))
                        .toList(),
                shipment.loadOrders().stream()
                        .map(order -> new Point(order.destinationLabel(), order.latitude(), order.longitude()))
                        .toList());
    }
}
