package za.co.trademesh.modules.shipment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Shipment(
        UUID id,
        UUID requestedByBusinessId,
        UUID clientRequestId,
        String inputFingerprint,
        UUID demandGroupSuggestionId,
        UUID capacitySearchId,
        UUID capacityReservationId,
        UUID capacityOfferId,
        UUID transporterId,
        BigDecimal reservedWeightKg,
        BigDecimal reservedVolumeCubicMetres,
        ShipmentStatus status,
        List<ShipmentLoadOrder> loadOrders,
        List<ShipmentAssignment> assignments,
        List<ShipmentTransition> transitions,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt) {

    public Shipment {
        loadOrders = List.copyOf(loadOrders);
        assignments = List.copyOf(assignments);
        transitions = List.copyOf(transitions);
    }

    public ShipmentAssignment currentAssignment() {
        return assignments.stream()
                .filter(ShipmentAssignment::active)
                .findFirst()
                .orElseThrow();
    }
}
