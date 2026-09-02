package za.co.trademesh.modules.shipment.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.shipment.domain.Shipment;
import za.co.trademesh.modules.shipment.domain.ShipmentRepository;

@Service
class ShipmentRiskQueryService implements ShipmentRiskCatalog {

    private final ShipmentRepository shipments;

    ShipmentRiskQueryService(ShipmentRepository shipments) {
        this.shipments = shipments;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShipmentRiskSnapshot> find(UUID shipmentId) {
        return shipments.findById(shipmentId).map(ShipmentRiskQueryService::snapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShipmentRiskSnapshot> findOperational(int limit) {
        return shipments.findOperational(limit).stream()
                .map(ShipmentRiskQueryService::snapshot)
                .toList();
    }

    private static ShipmentRiskSnapshot snapshot(Shipment shipment) {
        var assignment = shipment.currentAssignment();
        UUID previousDriverId = shipment.assignments().stream()
                .filter(candidate -> !candidate.active())
                .max(java.util.Comparator.comparingInt(candidate -> candidate.sequence()))
                .map(candidate -> candidate.driverId())
                .orElse(null);
        Instant deadline = shipment.loadOrders().stream()
                .map(order -> order.deliveryWindowEnd())
                .max(Instant::compareTo)
                .orElse(shipment.updatedAt());
        return new ShipmentRiskSnapshot(
                shipment.id(),
                shipment.requestedByBusinessId(),
                switch (shipment.status()) {
                    case AWAITING_COLLECTION -> RiskPhase.PRE_COLLECTION;
                    case COLLECTED -> RiskPhase.COLLECTED;
                    case IN_TRANSIT, DELAYED -> RiskPhase.MOVING;
                    case DELIVERED, DISPUTED, CANCELLED -> RiskPhase.TERMINAL;
                },
                assignment.id(),
                assignment.driverId(),
                previousDriverId,
                assignment.routeGeometry().stream()
                        .map(point -> new RoutePoint(point.latitude(), point.longitude()))
                        .toList(),
                deadline);
    }
}
