package za.co.trademesh.modules.shipment.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only shipment boundary exposed to operational risk rules. */
public interface ShipmentRiskCatalog {

    Optional<ShipmentRiskSnapshot> find(UUID shipmentId);

    List<ShipmentRiskSnapshot> findOperational(int limit);

    record ShipmentRiskSnapshot(
            UUID shipmentId,
            UUID businessId,
            RiskPhase phase,
            UUID assignmentId,
            UUID driverId,
            UUID previousDriverId,
            List<RoutePoint> approvedRoute,
            Instant deliveryDeadline) {

        public ShipmentRiskSnapshot {
            approvedRoute = List.copyOf(approvedRoute);
        }
    }

    record RoutePoint(double latitude, double longitude) {}

    enum RiskPhase {
        PRE_COLLECTION,
        COLLECTED,
        MOVING,
        TERMINAL
    }
}
