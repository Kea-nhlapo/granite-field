package za.co.trademesh.modules.shipment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ShipmentAssignment(
        UUID id,
        UUID commandId,
        String inputFingerprint,
        int sequence,
        UUID transporterId,
        UUID transportAssignmentId,
        UUID vehicleId,
        String vehicleRegistrationNumber,
        String vehicleDescription,
        UUID driverId,
        String driverDisplayName,
        String driverReference,
        UUID routeAssessmentId,
        UUID routeCalculationId,
        UUID routeCandidateId,
        String cargoProfile,
        String routeAlgorithmVersion,
        BigDecimal routeScore,
        BigDecimal routeConfidence,
        List<ShipmentRoutePoint> routeGeometry,
        long routeDistanceMetres,
        long routeDurationSeconds,
        BigDecimal routeTollEstimateZar,
        Instant startedAt,
        Instant endedAt,
        String reason,
        UUID correlationId,
        ShipmentActionSource source,
        UUID actorUserId) {

    public ShipmentAssignment {
        routeGeometry = List.copyOf(routeGeometry);
    }

    public boolean active() {
        return endedAt == null;
    }
}
