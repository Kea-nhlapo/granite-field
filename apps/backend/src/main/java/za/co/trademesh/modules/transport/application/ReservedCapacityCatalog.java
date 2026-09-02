package za.co.trademesh.modules.transport.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Reserved transport details exposed to shipment without leaking transport persistence. */
public interface ReservedCapacityCatalog {

    Optional<ReservedCapacity> claimReserved(UUID requestedByBusinessId, UUID searchId, UUID reservationId);

    Optional<TransportAssignment> findActiveAssignment(UUID transporterId, UUID assignmentId);

    record ReservedCapacity(
            UUID searchId,
            UUID reservationId,
            UUID demandGroupSuggestionId,
            UUID offerId,
            UUID transporterId,
            BigDecimal reservedWeightKg,
            BigDecimal reservedVolumeCubicMetres,
            Instant reservationExpiresAt,
            TransportAssignment assignment) {}

    record TransportAssignment(
            UUID transporterId,
            UUID assignmentId,
            UUID vehicleId,
            String vehicleRegistrationNumber,
            String vehicleDescription,
            BigDecimal vehicleMaximumWeightKg,
            BigDecimal vehicleMaximumVolumeCubicMetres,
            UUID driverId,
            String driverDisplayName,
            String driverReference) {}
}
