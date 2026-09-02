package za.co.trademesh.modules.transport.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.transport.application.TransportService;
import za.co.trademesh.modules.transport.domain.Capacity;
import za.co.trademesh.modules.transport.domain.CapacityOffer;
import za.co.trademesh.modules.transport.domain.CapacityOfferStatus;
import za.co.trademesh.modules.transport.domain.CargoRestriction;
import za.co.trademesh.modules.transport.domain.Driver;
import za.co.trademesh.modules.transport.domain.DriverStatus;
import za.co.trademesh.modules.transport.domain.DriverVehicleAssignment;
import za.co.trademesh.modules.transport.domain.RoutePoint;
import za.co.trademesh.modules.transport.domain.TransporterProfile;
import za.co.trademesh.modules.transport.domain.TransporterStatus;
import za.co.trademesh.modules.transport.domain.Vehicle;
import za.co.trademesh.modules.transport.domain.VehicleStatus;

public final class TransportContracts {

    private TransportContracts() {}

    public record RegisterTransporterRequest(
            @NotBlank @Size(max = 255) String displayName) {}

    public record CreateVehicleRequest(
            @NotNull UUID requestId,
            @NotBlank @Size(max = 32) String registrationNumber,
            @NotBlank @Size(max = 255) String description,

            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 3)
            BigDecimal maximumWeightKg,

            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 3)
            BigDecimal maximumVolumeCubicMetres) {

        TransportService.CreateVehicle toCommand() {
            return new TransportService.CreateVehicle(
                    requestId, registrationNumber, description, maximumWeightKg, maximumVolumeCubicMetres);
        }
    }

    public record CreateDriverRequest(
            @NotNull UUID requestId,
            @NotBlank @Size(max = 255) String displayName,
            @NotBlank @Size(max = 100) String driverReference) {

        TransportService.CreateDriver toCommand() {
            return new TransportService.CreateDriver(requestId, displayName, driverReference);
        }
    }

    public record AssignDriverRequest(
            @NotNull UUID requestId,
            @NotNull UUID vehicleId,
            @NotNull UUID driverId) {

        TransportService.AssignDriver toCommand() {
            return new TransportService.AssignDriver(requestId, vehicleId, driverId);
        }
    }

    public record PublishCapacityOfferRequest(
            @NotNull UUID requestId,
            @NotNull UUID vehicleId,
            @NotNull UUID driverAssignmentId,
            @NotEmpty @Size(min = 2, max = 100) List<@Valid RoutePointRequest> routePoints,
            int corridorRadiusMetres,
            @NotNull Instant departureWindowStart,
            @NotNull Instant departureWindowEnd,
            @NotNull Instant expiresAt,
            @NotNull @Size(max = 5) List<@NotNull CargoRestriction> restrictions,
            @NotNull @Valid CapacityRequest capacity) {

        TransportService.PublishCapacityOffer toCommand() {
            return new TransportService.PublishCapacityOffer(
                    requestId,
                    vehicleId,
                    driverAssignmentId,
                    routePoints.stream().map(RoutePointRequest::toDomain).toList(),
                    corridorRadiusMetres,
                    departureWindowStart,
                    departureWindowEnd,
                    expiresAt,
                    restrictions,
                    capacity.toDomain());
        }
    }

    public record RoutePointRequest(@Size(max = 255) String label, double latitude, double longitude) {

        RoutePoint toDomain() {
            return new RoutePoint(0, label, latitude, longitude);
        }
    }

    public record CapacityRequest(
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 3)
            BigDecimal weightKg,

            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 3)
            BigDecimal volumeCubicMetres) {

        Capacity toDomain() {
            return new Capacity(weightKg, volumeCubicMetres);
        }
    }

    public record TransporterResponse(
            UUID id, UUID businessId, String displayName, TransporterStatus status, Instant createdAt) {

        static TransporterResponse from(TransporterProfile transporter) {
            return new TransporterResponse(
                    transporter.id(),
                    transporter.businessId(),
                    transporter.displayName(),
                    transporter.status(),
                    transporter.createdAt());
        }
    }

    public record VehicleResponse(
            UUID id,
            UUID transporterId,
            String registrationNumber,
            String description,
            CapacityResponse maximumCapacity,
            VehicleStatus status,
            Instant createdAt) {

        static VehicleResponse from(Vehicle vehicle) {
            return new VehicleResponse(
                    vehicle.id(),
                    vehicle.transporterId(),
                    vehicle.registrationNumber(),
                    vehicle.description(),
                    new CapacityResponse(vehicle.maximumWeightKg(), vehicle.maximumVolumeCubicMetres()),
                    vehicle.status(),
                    vehicle.createdAt());
        }
    }

    public record DriverResponse(
            UUID id,
            UUID transporterId,
            String displayName,
            String driverReference,
            DriverStatus status,
            Instant createdAt) {

        static DriverResponse from(Driver driver) {
            return new DriverResponse(
                    driver.id(),
                    driver.transporterId(),
                    driver.displayName(),
                    driver.driverReference(),
                    driver.status(),
                    driver.createdAt());
        }
    }

    public record AssignmentResponse(
            UUID id,
            UUID transporterId,
            UUID vehicleId,
            UUID driverId,
            Instant startedAt,
            Instant endedAt,
            boolean active) {

        static AssignmentResponse from(DriverVehicleAssignment assignment) {
            return new AssignmentResponse(
                    assignment.id(),
                    assignment.transporterId(),
                    assignment.vehicleId(),
                    assignment.driverId(),
                    assignment.startedAt(),
                    assignment.endedAt(),
                    assignment.active());
        }
    }

    public record CapacityOfferResponse(
            UUID id,
            UUID transporterId,
            UUID vehicleId,
            UUID driverAssignmentId,
            List<RoutePointResponse> routePoints,
            int corridorRadiusMetres,
            DepartureWindowResponse departureWindow,
            Instant expiresAt,
            List<CargoRestriction> restrictions,
            CapacityResponse totalCapacity,
            CapacityResponse remainingCapacity,
            CapacityOfferStatus status,
            Instant createdAt,
            Instant cancelledAt) {

        static CapacityOfferResponse from(CapacityOffer offer) {
            return new CapacityOfferResponse(
                    offer.id(),
                    offer.transporterId(),
                    offer.vehicleId(),
                    offer.driverAssignmentId(),
                    offer.routePoints().stream().map(RoutePointResponse::from).toList(),
                    offer.corridorRadiusMetres(),
                    new DepartureWindowResponse(offer.departureWindowStart(), offer.departureWindowEnd()),
                    offer.expiresAt(),
                    offer.restrictions(),
                    CapacityResponse.from(offer.totalCapacity()),
                    CapacityResponse.from(offer.remainingCapacity()),
                    offer.status(),
                    offer.createdAt(),
                    offer.cancelledAt());
        }
    }

    public record RoutePointResponse(int sequence, String label, double latitude, double longitude) {

        static RoutePointResponse from(RoutePoint point) {
            return new RoutePointResponse(point.sequence(), point.label(), point.latitude(), point.longitude());
        }
    }

    public record CapacityResponse(BigDecimal weightKg, BigDecimal volumeCubicMetres) {

        static CapacityResponse from(Capacity capacity) {
            return new CapacityResponse(capacity.weightKg(), capacity.volumeCubicMetres());
        }
    }

    public record DepartureWindowResponse(Instant start, Instant end) {}
}
