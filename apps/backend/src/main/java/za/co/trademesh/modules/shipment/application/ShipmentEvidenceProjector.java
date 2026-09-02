package za.co.trademesh.modules.shipment.application;

import org.springframework.stereotype.Component;
import za.co.trademesh.modules.evidence.application.EvidenceMetadata;
import za.co.trademesh.modules.evidence.application.EvidenceProjection;
import za.co.trademesh.modules.evidence.application.EvidenceProjector;
import za.co.trademesh.modules.shipment.events.ShipmentEvent;
import za.co.trademesh.shared.events.DomainEvent;

@Component
class ShipmentEvidenceProjector implements EvidenceProjector {

    @Override
    public boolean supports(DomainEvent event) {
        return event instanceof ShipmentEvent;
    }

    @Override
    public EvidenceProjection project(DomainEvent event) {
        return switch ((ShipmentEvent) event) {
            case ShipmentEvent.ShipmentCreated created ->
                new EvidenceProjection(
                        "SHIPMENT",
                        created.shipmentId(),
                        created.shipmentId(),
                        EvidenceMetadata.of(
                                "requestedByBusinessId",
                                created.requestedByBusinessId(),
                                "demandGroupSuggestionId",
                                created.demandGroupSuggestionId(),
                                "capacityReservationId",
                                created.capacityReservationId(),
                                "routeCandidateId",
                                created.routeCandidateId()));
            case ShipmentEvent.ShipmentStatusChanged changed ->
                new EvidenceProjection(
                        "SHIPMENT",
                        changed.shipmentId(),
                        changed.shipmentId(),
                        EvidenceMetadata.of(
                                "fromStatus",
                                changed.fromStatus(),
                                "toStatus",
                                changed.toStatus(),
                                "actionSource",
                                changed.source()));
            case ShipmentEvent.ShipmentAssignmentChanged changed ->
                new EvidenceProjection(
                        "SHIPMENT",
                        changed.shipmentId(),
                        changed.shipmentId(),
                        EvidenceMetadata.of(
                                "previousAssignmentId",
                                changed.previousAssignmentId(),
                                "assignmentId",
                                changed.assignmentId(),
                                "vehicleId",
                                changed.vehicleId(),
                                "driverId",
                                changed.driverId(),
                                "routeCandidateId",
                                changed.routeCandidateId()));
        };
    }
}
