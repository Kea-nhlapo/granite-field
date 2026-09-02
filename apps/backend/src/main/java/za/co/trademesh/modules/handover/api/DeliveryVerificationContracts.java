package za.co.trademesh.modules.handover.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.handover.application.HandoverService;
import za.co.trademesh.modules.handover.domain.HandoverState;

final class DeliveryVerificationContracts {

    private DeliveryVerificationContracts() {}

    record IssueQrRequest(
            @NotNull UUID businessId,
            @NotNull UUID deliveryOrderId,
            @NotNull UUID counterpartyUserId) {}

    record ScanRequest(
            @NotNull UUID requestId,
            @NotBlank @Size(max = 1024) String token,

            @NotNull @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4)
            BigDecimal capturedQty,

            @Size(max = 2048) String photoUrl,
            @DecimalMin("-90.0") @DecimalMax("90.0") double gpsLat,
            @DecimalMin("-180.0") @DecimalMax("180.0") double gpsLng) {

        HandoverService.ScanDelivery toCommand() {
            return new HandoverService.ScanDelivery(requestId, token, capturedQty, photoUrl, gpsLat, gpsLng);
        }
    }

    record StatusResponse(
            UUID shipmentId,
            UUID businessId,
            String verificationStatus,
            List<DeliveryCheckResponse> deliveries,
            BigDecimal resolvedAmount,
            Instant updatedAt) {

        static StatusResponse from(HandoverService.DeliveryStatus status) {
            return new StatusResponse(
                    status.shipmentId(),
                    status.businessId(),
                    status.verificationStatus(),
                    status.deliveries().stream()
                            .map(DeliveryCheckResponse::from)
                            .toList(),
                    status.resolvedAmount(),
                    status.updatedAt());
        }
    }

    record DeliveryCheckResponse(
            UUID challengeId,
            UUID deliveryOrderId,
            HandoverState state,
            BigDecimal expectedQuantity,
            BigDecimal capturedQuantity,
            String unitOfMeasure,
            String photoUrl,
            Instant completedAt) {

        static DeliveryCheckResponse from(HandoverService.DeliveryCheck check) {
            return new DeliveryCheckResponse(
                    check.challengeId(),
                    check.deliveryOrderId(),
                    check.state(),
                    check.expectedQuantity(),
                    check.capturedQuantity(),
                    check.unitOfMeasure(),
                    check.photoUrl(),
                    check.completedAt());
        }
    }
}
