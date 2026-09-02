package za.co.trademesh.modules.shipment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only shipment facts exposed to an already-authorized insurance case. */
public interface ShipmentInsuranceCatalog {

    Optional<ShipmentSnapshot> find(UUID shipmentId);

    record ShipmentSnapshot(
            UUID shipmentId,
            UUID businessId,
            String status,
            BigDecimal reservedWeightKg,
            BigDecimal reservedVolumeCubicMetres,
            List<CargoStop> cargoStops,
            List<Assignment> assignments,
            List<StatusChange> statusHistory,
            Instant createdAt,
            Instant updatedAt) {
        public ShipmentSnapshot {
            cargoStops = List.copyOf(cargoStops);
            assignments = List.copyOf(assignments);
            statusHistory = List.copyOf(statusHistory);
        }
    }

    record CargoStop(
            UUID orderId,
            UUID buyerBusinessId,
            String destinationLabel,
            Instant deliveryWindowStart,
            Instant deliveryWindowEnd,
            List<CargoItem> items) {
        public CargoStop {
            items = List.copyOf(items);
        }
    }

    record CargoItem(String productCode, String unitOfMeasure) {}

    record Assignment(
            UUID assignmentId,
            UUID vehicleId,
            String vehicleRegistrationNumber,
            String vehicleDescription,
            UUID driverId,
            String driverDisplayName,
            String driverReference,
            UUID routeCandidateId,
            String cargoProfile,
            String routeAlgorithmVersion,
            BigDecimal routeScore,
            BigDecimal routeConfidence,
            List<RoutePoint> approvedRoute,
            long distanceMetres,
            long durationSeconds,
            BigDecimal tollEstimateZar,
            Instant startedAt,
            Instant endedAt) {
        public Assignment {
            approvedRoute = List.copyOf(approvedRoute);
        }
    }

    record RoutePoint(double latitude, double longitude) {}

    record StatusChange(String fromStatus, String toStatus, Instant occurredAt, String source) {}
}
