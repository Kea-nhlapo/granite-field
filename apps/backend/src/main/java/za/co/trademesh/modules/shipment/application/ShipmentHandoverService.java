package za.co.trademesh.modules.shipment.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.shipment.domain.Shipment;
import za.co.trademesh.modules.shipment.domain.ShipmentActionSource;
import za.co.trademesh.modules.shipment.domain.ShipmentRepository;
import za.co.trademesh.modules.shipment.domain.ShipmentStatus;

@Service
class ShipmentHandoverService implements ShipmentHandoverCatalog {

    private final ShipmentRepository repository;
    private final ShipmentService shipments;

    ShipmentHandoverService(ShipmentRepository repository, ShipmentService shipments) {
        this.repository = repository;
        this.shipments = shipments;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<HandoverShipment> findOwned(UUID businessId, UUID shipmentId) {
        return repository
                .findById(shipmentId)
                .filter(shipment -> shipment.requestedByBusinessId().equals(businessId)
                        || shipment.loadOrders().stream()
                                .anyMatch(order -> order.buyerBusinessId().equals(businessId)))
                .map(ShipmentHandoverService::snapshot);
    }

    @Override
    public void complete(
            UUID businessId,
            UUID shipmentId,
            UUID commandId,
            Completion completion,
            String reason,
            UUID correlationId,
            UUID actorUserId) {
        ShipmentStatus target =
                switch (completion) {
                    case COLLECTION_VERIFIED -> ShipmentStatus.COLLECTED;
                    case DELIVERY_VERIFIED -> ShipmentStatus.DELIVERED;
                    case DELIVERY_DISPUTED -> ShipmentStatus.DISPUTED;
                };
        UUID shipmentOwner = repository
                .findById(shipmentId)
                .map(shipment -> shipment.requestedByBusinessId())
                .orElse(businessId);
        shipments.transition(
                shipmentOwner,
                shipmentId,
                new ShipmentService.TransitionShipment(commandId, target, reason, correlationId),
                actorUserId,
                ShipmentActionSource.HANDOVER);
    }

    private static HandoverShipment snapshot(Shipment shipment) {
        var route = shipment.currentAssignment().routeGeometry();
        var origin = route.getFirst();
        return new HandoverShipment(
                shipment.id(),
                shipment.requestedByBusinessId(),
                stage(shipment.status()),
                new Location("Collection point", origin.latitude(), origin.longitude()),
                shipment.loadOrders().stream()
                        .map(order -> new DeliveryStop(
                                order.orderId(),
                                order.buyerBusinessId(),
                                new Location(order.destinationLabel(), order.latitude(), order.longitude())))
                        .toList(),
                shipment.updatedAt());
    }

    private static Stage stage(ShipmentStatus status) {
        return switch (status) {
            case AWAITING_COLLECTION -> Stage.AWAITING_COLLECTION;
            case COLLECTED -> Stage.COLLECTED;
            case IN_TRANSIT -> Stage.IN_TRANSIT;
            case DELAYED -> Stage.DELAYED;
            case DELIVERED, DISPUTED, CANCELLED -> Stage.TERMINAL;
        };
    }
}
