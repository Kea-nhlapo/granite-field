package za.co.trademesh.modules.shipment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.shipment.application.ShipmentService;
import za.co.trademesh.modules.shipment.domain.Shipment;
import za.co.trademesh.modules.shipment.domain.ShipmentActionSource;
import za.co.trademesh.modules.shipment.domain.ShipmentAssignment;
import za.co.trademesh.modules.shipment.domain.ShipmentCargoItem;
import za.co.trademesh.modules.shipment.domain.ShipmentLoadOrder;
import za.co.trademesh.modules.shipment.domain.ShipmentRoutePoint;
import za.co.trademesh.modules.shipment.domain.ShipmentStatus;
import za.co.trademesh.modules.shipment.domain.ShipmentTransition;

public final class ShipmentContracts {

    private ShipmentContracts() {}

    public record CreateShipmentRequest(
            @NotNull UUID requestId,
            @NotNull UUID demandGroupSuggestionId,
            @NotNull UUID capacitySearchId,
            @NotNull UUID capacityReservationId,
            @NotNull UUID routeAssessmentId,
            @NotNull UUID routeCandidateId,
            @NotBlank @Size(max = 500) String reason,
            @NotNull UUID correlationId) {

        ShipmentService.CreateShipment toCommand() {
            return new ShipmentService.CreateShipment(
                    requestId,
                    demandGroupSuggestionId,
                    capacitySearchId,
                    capacityReservationId,
                    routeAssessmentId,
                    routeCandidateId,
                    reason,
                    correlationId);
        }
    }

    public record TransitionShipmentRequest(
            @NotNull UUID commandId,
            @NotNull ShipmentStatus targetStatus,
            @NotBlank @Size(max = 500) String reason,
            @NotNull UUID correlationId) {

        ShipmentService.TransitionShipment toCommand() {
            return new ShipmentService.TransitionShipment(commandId, targetStatus, reason, correlationId);
        }
    }

    public record ChangeAssignmentRequest(
            @NotNull UUID commandId,
            @NotNull UUID transportAssignmentId,
            @NotNull UUID routeAssessmentId,
            @NotNull UUID routeCandidateId,
            @NotBlank @Size(max = 500) String reason,
            @NotNull UUID correlationId) {

        ShipmentService.ChangeAssignment toCommand() {
            return new ShipmentService.ChangeAssignment(
                    commandId, transportAssignmentId, routeAssessmentId, routeCandidateId, reason, correlationId);
        }
    }

    public record ShipmentResponse(
            UUID shipmentId,
            UUID requestedByBusinessId,
            UUID demandGroupSuggestionId,
            UUID capacitySearchId,
            UUID capacityReservationId,
            UUID capacityOfferId,
            UUID transporterId,
            CapacityResponse reservedCapacity,
            ShipmentStatus status,
            List<LoadOrderResponse> loadOrders,
            AssignmentResponse currentAssignment,
            List<AssignmentResponse> assignmentHistory,
            List<TransitionResponse> transitionHistory,
            Instant createdAt,
            Instant updatedAt) {

        static ShipmentResponse from(Shipment shipment) {
            return new ShipmentResponse(
                    shipment.id(),
                    shipment.requestedByBusinessId(),
                    shipment.demandGroupSuggestionId(),
                    shipment.capacitySearchId(),
                    shipment.capacityReservationId(),
                    shipment.capacityOfferId(),
                    shipment.transporterId(),
                    new CapacityResponse(shipment.reservedWeightKg(), shipment.reservedVolumeCubicMetres()),
                    shipment.status(),
                    shipment.loadOrders().stream().map(LoadOrderResponse::from).toList(),
                    AssignmentResponse.from(shipment.currentAssignment()),
                    shipment.assignments().stream()
                            .map(AssignmentResponse::from)
                            .toList(),
                    shipment.transitions().stream()
                            .map(TransitionResponse::from)
                            .toList(),
                    shipment.createdAt(),
                    shipment.updatedAt());
        }
    }

    public record CapacityResponse(BigDecimal weightKg, BigDecimal volumeCubicMetres) {}

    public record LoadOrderResponse(
            UUID orderId,
            UUID buyerBusinessId,
            String destinationLabel,
            double latitude,
            double longitude,
            Instant deliveryWindowStart,
            Instant deliveryWindowEnd,
            List<CargoItemResponse> cargoItems) {

        static LoadOrderResponse from(ShipmentLoadOrder order) {
            return new LoadOrderResponse(
                    order.orderId(),
                    order.buyerBusinessId(),
                    order.destinationLabel(),
                    order.latitude(),
                    order.longitude(),
                    order.deliveryWindowStart(),
                    order.deliveryWindowEnd(),
                    order.cargoItems().stream().map(CargoItemResponse::from).toList());
        }
    }

    public record CargoItemResponse(String productCode, String unitOfMeasure) {
        static CargoItemResponse from(ShipmentCargoItem item) {
            return new CargoItemResponse(item.productCode(), item.unitOfMeasure());
        }
    }

    public record AssignmentResponse(
            UUID assignmentId,
            int sequence,
            UUID transportAssignmentId,
            UUID vehicleId,
            String vehicleRegistrationNumber,
            String vehicleDescription,
            UUID driverId,
            String driverDisplayName,
            UUID routeAssessmentId,
            UUID routeCalculationId,
            UUID routeCandidateId,
            String cargoProfile,
            String routeAlgorithmVersion,
            BigDecimal routeScore,
            BigDecimal routeConfidence,
            List<RoutePointResponse> routeGeometry,
            long routeDistanceMetres,
            long routeDurationSeconds,
            BigDecimal routeTollEstimateZar,
            Instant startedAt,
            Instant endedAt,
            String reason,
            UUID correlationId,
            ShipmentActionSource source) {

        static AssignmentResponse from(ShipmentAssignment assignment) {
            return new AssignmentResponse(
                    assignment.id(),
                    assignment.sequence(),
                    assignment.transportAssignmentId(),
                    assignment.vehicleId(),
                    assignment.vehicleRegistrationNumber(),
                    assignment.vehicleDescription(),
                    assignment.driverId(),
                    assignment.driverDisplayName(),
                    assignment.routeAssessmentId(),
                    assignment.routeCalculationId(),
                    assignment.routeCandidateId(),
                    assignment.cargoProfile(),
                    assignment.routeAlgorithmVersion(),
                    assignment.routeScore(),
                    assignment.routeConfidence(),
                    assignment.routeGeometry().stream()
                            .map(RoutePointResponse::from)
                            .toList(),
                    assignment.routeDistanceMetres(),
                    assignment.routeDurationSeconds(),
                    assignment.routeTollEstimateZar(),
                    assignment.startedAt(),
                    assignment.endedAt(),
                    assignment.reason(),
                    assignment.correlationId(),
                    assignment.source());
        }
    }

    public record RoutePointResponse(double latitude, double longitude) {
        static RoutePointResponse from(ShipmentRoutePoint point) {
            return new RoutePointResponse(point.latitude(), point.longitude());
        }
    }

    public record TransitionResponse(
            UUID transitionId,
            ShipmentStatus fromStatus,
            ShipmentStatus toStatus,
            UUID actorUserId,
            Instant occurredAt,
            String reason,
            UUID correlationId,
            ShipmentActionSource source) {

        static TransitionResponse from(ShipmentTransition transition) {
            return new TransitionResponse(
                    transition.id(),
                    transition.fromStatus(),
                    transition.toStatus(),
                    transition.actorUserId(),
                    transition.occurredAt(),
                    transition.reason(),
                    transition.correlationId(),
                    transition.source());
        }
    }
}
