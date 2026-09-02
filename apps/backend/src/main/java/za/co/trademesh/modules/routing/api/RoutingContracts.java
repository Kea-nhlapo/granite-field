package za.co.trademesh.modules.routing.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.routing.application.RoutingService;
import za.co.trademesh.modules.routing.domain.CandidateRoute;
import za.co.trademesh.modules.routing.domain.GeoPoint;
import za.co.trademesh.modules.routing.domain.RouteAvoidance;
import za.co.trademesh.modules.routing.domain.RouteCalculation;
import za.co.trademesh.modules.routing.domain.RouteSegment;
import za.co.trademesh.modules.routing.domain.VehicleLimits;

public final class RoutingContracts {

    private RoutingContracts() {}

    public record CalculateRoutesRequest(
            @NotNull UUID requestId,
            UUID recalculationOfId,
            @NotNull @Valid PointRequest origin,
            @NotNull @Valid PointRequest destination,
            @NotNull @Size(max = 20) List<@NotNull @Valid PointRequest> waypoints,
            @NotNull @Valid VehicleLimitsRequest vehicleLimits,
            @NotNull List<@NotNull RouteAvoidance> avoidances) {

        RoutingService.CalculateRoutes toCommand() {
            return new RoutingService.CalculateRoutes(
                    requestId,
                    recalculationOfId,
                    origin.toDomain(),
                    destination.toDomain(),
                    waypoints.stream().map(PointRequest::toDomain).toList(),
                    vehicleLimits.toDomain(),
                    avoidances);
        }
    }

    public record PointRequest(
            @Size(max = 255) String label,
            @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") double longitude) {

        GeoPoint toDomain() {
            return new GeoPoint(label, latitude, longitude);
        }
    }

    public record VehicleLimitsRequest(
            @NotNull @DecimalMin("0.001") @Digits(integer = 12, fraction = 3)
            BigDecimal maximumWeightKg,

            @NotNull @DecimalMin("0.001") @Digits(integer = 5, fraction = 3)
            BigDecimal maximumHeightMetres,

            @NotNull @DecimalMin("0.001") @Digits(integer = 5, fraction = 3)
            BigDecimal maximumWidthMetres,

            @NotNull @DecimalMin("0.001") @Digits(integer = 5, fraction = 3)
            BigDecimal maximumLengthMetres) {

        VehicleLimits toDomain() {
            return new VehicleLimits(maximumWeightKg, maximumHeightMetres, maximumWidthMetres, maximumLengthMetres);
        }
    }

    public record RouteCalculationResponse(
            UUID calculationId,
            UUID requestedByBusinessId,
            UUID requestId,
            UUID recalculationOfId,
            PointResponse origin,
            PointResponse destination,
            List<PointResponse> waypoints,
            VehicleLimitsResponse vehicleLimits,
            List<RouteAvoidance> avoidances,
            String providerName,
            String providerVersion,
            boolean fallbackUsed,
            String fallbackReason,
            List<CandidateResponse> candidates,
            Instant createdAt) {

        static RouteCalculationResponse from(RouteCalculation calculation) {
            return new RouteCalculationResponse(
                    calculation.id(),
                    calculation.requestedByBusinessId(),
                    calculation.clientRequestId(),
                    calculation.recalculationOfId(),
                    PointResponse.from(calculation.origin()),
                    PointResponse.from(calculation.destination()),
                    calculation.waypoints().stream().map(PointResponse::from).toList(),
                    VehicleLimitsResponse.from(calculation.vehicleLimits()),
                    calculation.avoidances(),
                    calculation.providerName(),
                    calculation.providerVersion(),
                    calculation.fallbackUsed(),
                    calculation.fallbackReason(),
                    calculation.candidates().stream()
                            .map(CandidateResponse::from)
                            .toList(),
                    calculation.createdAt());
        }
    }

    public record CandidateResponse(
            UUID candidateId,
            int sequence,
            String label,
            List<PointResponse> geometry,
            long distanceMetres,
            long durationSeconds,
            BigDecimal tollEstimateZar,
            List<SegmentResponse> segments) {

        static CandidateResponse from(CandidateRoute candidate) {
            return new CandidateResponse(
                    candidate.id(),
                    candidate.sequence(),
                    candidate.label(),
                    candidate.geometry().stream().map(PointResponse::geometry).toList(),
                    candidate.distanceMetres(),
                    candidate.durationSeconds(),
                    candidate.tollEstimateZar(),
                    candidate.segments().stream().map(SegmentResponse::from).toList());
        }
    }

    public record SegmentResponse(
            UUID segmentId,
            int sequence,
            String fromLabel,
            String toLabel,
            List<PointResponse> geometry,
            long distanceMetres,
            long durationSeconds,
            BigDecimal tollEstimateZar) {

        static SegmentResponse from(RouteSegment segment) {
            return new SegmentResponse(
                    segment.id(),
                    segment.sequence(),
                    segment.fromLabel(),
                    segment.toLabel(),
                    segment.geometry().stream().map(PointResponse::geometry).toList(),
                    segment.distanceMetres(),
                    segment.durationSeconds(),
                    segment.tollEstimateZar());
        }
    }

    public record PointResponse(String label, double latitude, double longitude) {
        static PointResponse from(GeoPoint point) {
            return new PointResponse(point.label(), point.latitude(), point.longitude());
        }

        static PointResponse geometry(GeoPoint point) {
            return new PointResponse(null, point.latitude(), point.longitude());
        }
    }

    public record VehicleLimitsResponse(
            BigDecimal maximumWeightKg,
            BigDecimal maximumHeightMetres,
            BigDecimal maximumWidthMetres,
            BigDecimal maximumLengthMetres) {
        static VehicleLimitsResponse from(VehicleLimits limits) {
            return new VehicleLimitsResponse(
                    limits.maximumWeightKg(),
                    limits.maximumHeightMetres(),
                    limits.maximumWidthMetres(),
                    limits.maximumLengthMetres());
        }
    }
}
