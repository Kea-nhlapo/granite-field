package za.co.trademesh.modules.shipment.events;

import java.util.UUID;
import za.co.trademesh.modules.shipment.domain.ShipmentActionSource;
import za.co.trademesh.modules.shipment.domain.ShipmentStatus;
import za.co.trademesh.shared.events.DomainEvent;

public sealed interface ShipmentEvent extends DomainEvent
        permits ShipmentEvent.ShipmentCreated,
                ShipmentEvent.ShipmentStatusChanged,
                ShipmentEvent.ShipmentAssignmentChanged {

    @Override
    default int schemaVersion() {
        return 1;
    }

    record ShipmentCreated(
            UUID shipmentId,
            UUID requestedByBusinessId,
            UUID demandGroupSuggestionId,
            UUID capacityReservationId,
            UUID routeCandidateId)
            implements ShipmentEvent {
        @Override
        public String type() {
            return "SHIPMENT_CREATED";
        }
    }

    record ShipmentStatusChanged(
            UUID shipmentId,
            ShipmentStatus fromStatus,
            ShipmentStatus toStatus,
            UUID correlationId,
            ShipmentActionSource source)
            implements ShipmentEvent {
        @Override
        public String type() {
            return "SHIPMENT_STATUS_CHANGED";
        }
    }

    record ShipmentAssignmentChanged(
            UUID shipmentId,
            UUID previousAssignmentId,
            UUID assignmentId,
            UUID vehicleId,
            UUID driverId,
            UUID routeCandidateId,
            UUID correlationId)
            implements ShipmentEvent {
        @Override
        public String type() {
            return "SHIPMENT_ASSIGNMENT_CHANGED";
        }
    }
}
