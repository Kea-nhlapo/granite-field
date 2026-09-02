package za.co.trademesh.modules.shipment.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.shipment.domain.Shipment;
import za.co.trademesh.modules.shipment.domain.ShipmentRepository;

@Service
class ShipmentInsuranceService implements ShipmentInsuranceCatalog {

    private final ShipmentRepository shipments;

    ShipmentInsuranceService(ShipmentRepository shipments) {
        this.shipments = shipments;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShipmentSnapshot> find(UUID shipmentId) {
        return shipments.findById(shipmentId).map(ShipmentInsuranceService::snapshot);
    }

    private static ShipmentSnapshot snapshot(Shipment shipment) {
        return new ShipmentSnapshot(
                shipment.id(),
                shipment.requestedByBusinessId(),
                shipment.status().name(),
                shipment.reservedWeightKg(),
                shipment.reservedVolumeCubicMetres(),
                shipment.loadOrders().stream()
                        .map(order -> new CargoStop(
                                order.orderId(),
                                order.buyerBusinessId(),
                                order.destinationLabel(),
                                order.deliveryWindowStart(),
                                order.deliveryWindowEnd(),
                                order.cargoItems().stream()
                                        .map(item -> new CargoItem(item.productCode(), item.unitOfMeasure()))
                                        .toList()))
                        .toList(),
                shipment.assignments().stream()
                        .map(assignment -> new Assignment(
                                assignment.id(),
                                assignment.vehicleId(),
                                assignment.vehicleRegistrationNumber(),
                                assignment.vehicleDescription(),
                                assignment.driverId(),
                                assignment.driverDisplayName(),
                                assignment.driverReference(),
                                assignment.routeCandidateId(),
                                assignment.cargoProfile(),
                                assignment.routeAlgorithmVersion(),
                                assignment.routeScore(),
                                assignment.routeConfidence(),
                                assignment.routeGeometry().stream()
                                        .map(point -> new RoutePoint(point.latitude(), point.longitude()))
                                        .toList(),
                                assignment.routeDistanceMetres(),
                                assignment.routeDurationSeconds(),
                                assignment.routeTollEstimateZar(),
                                assignment.startedAt(),
                                assignment.endedAt()))
                        .toList(),
                shipment.transitions().stream()
                        .map(transition -> new StatusChange(
                                transition.fromStatus() == null
                                        ? null
                                        : transition.fromStatus().name(),
                                transition.toStatus().name(),
                                transition.occurredAt(),
                                transition.source().name()))
                        .toList(),
                shipment.createdAt(),
                shipment.updatedAt());
    }
}
