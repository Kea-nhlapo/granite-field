package za.co.trademesh.modules.shipment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository {

    boolean save(Shipment shipment);

    Optional<Shipment> findById(UUID businessId, UUID shipmentId);

    Optional<Shipment> findById(UUID shipmentId);

    List<Shipment> findOperational(int limit);

    Optional<Shipment> findByIdForUpdate(UUID businessId, UUID shipmentId);

    Optional<Shipment> findByRequestId(UUID businessId, UUID requestId);

    Optional<ShipmentTransition> findTransitionByCommandId(UUID shipmentId, UUID commandId);

    boolean addTransition(UUID shipmentId, ShipmentStatus expectedStatus, ShipmentTransition transition);

    Optional<ShipmentAssignment> findAssignmentByCommandId(UUID shipmentId, UUID commandId);

    boolean replaceAssignment(
            UUID shipmentId, UUID currentAssignmentId, ShipmentAssignment replacement, Instant endedAt);
}
