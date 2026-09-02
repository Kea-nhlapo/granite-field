package za.co.trademesh.modules.handover.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.handover.application.HandoverService;
import za.co.trademesh.modules.handover.domain.CaptureMode;
import za.co.trademesh.modules.handover.domain.HandoverChallenge;
import za.co.trademesh.modules.handover.domain.HandoverConfirmation;
import za.co.trademesh.modules.handover.domain.HandoverParty;
import za.co.trademesh.modules.handover.domain.HandoverState;
import za.co.trademesh.modules.handover.domain.HandoverType;
import za.co.trademesh.modules.handover.domain.QuantityOutcome;

final class HandoverContracts {

    private HandoverContracts() {}

    record IssueChallengeRequest(
            @NotNull HandoverType type,
            UUID deliveryOrderId,
            @NotNull UUID counterpartyUserId) {

        HandoverService.IssueChallenge toCommand() {
            return new HandoverService.IssueChallenge(type, deliveryOrderId, counterpartyUserId);
        }
    }

    record ConfirmHandoverRequest(
            @NotNull UUID commandId,
            @NotBlank @Size(max = 1024) String qrPayload,
            @NotNull CaptureMode captureMode,
            @NotNull Instant observedAt,
            @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
            @NotNull QuantityOutcome quantityOutcome,
            @Size(max = 500) String quantityNote) {

        HandoverService.ConfirmHandover toCommand() {
            return new HandoverService.ConfirmHandover(
                    commandId, qrPayload, captureMode, observedAt, latitude, longitude, quantityOutcome, quantityNote);
        }
    }

    record IssuedChallengeResponse(ChallengeResponse challenge, String qrPayload) {
        static IssuedChallengeResponse from(HandoverService.IssuedChallenge issued) {
            return new IssuedChallengeResponse(ChallengeResponse.from(issued.challenge()), issued.qrPayload());
        }
    }

    record ChallengeResponse(
            UUID challengeId,
            UUID shipmentId,
            HandoverType type,
            UUID deliveryOrderId,
            HandoverState state,
            BigDecimal expectedQuantity,
            String unitOfMeasure,
            UUID initiatorUserId,
            UUID counterpartyUserId,
            LocationResponse expectedLocation,
            int locationToleranceMetres,
            Instant expiresAt,
            Instant completedAt,
            List<ConfirmationResponse> confirmations) {

        static ChallengeResponse from(HandoverChallenge challenge) {
            return new ChallengeResponse(
                    challenge.id(),
                    challenge.shipmentId(),
                    challenge.type(),
                    challenge.deliveryOrderId(),
                    challenge.state(),
                    challenge.expectedQuantity(),
                    challenge.unitOfMeasure(),
                    challenge.initiatorUserId(),
                    challenge.counterpartyUserId(),
                    new LocationResponse(
                            challenge.expectedLocation().label(),
                            challenge.expectedLocation().latitude(),
                            challenge.expectedLocation().longitude()),
                    challenge.locationToleranceMetres(),
                    challenge.expiresAt(),
                    challenge.completedAt(),
                    challenge.confirmations().stream()
                            .map(ConfirmationResponse::from)
                            .toList());
        }
    }

    record LocationResponse(String label, double latitude, double longitude) {}

    record ConfirmationResponse(
            UUID confirmationId,
            UUID actorUserId,
            HandoverParty party,
            Instant observedAt,
            Instant receivedAt,
            double latitude,
            double longitude,
            double distanceMetres,
            BigDecimal capturedQuantity,
            String photoUrl,
            QuantityOutcome quantityOutcome,
            String quantityNote) {

        static ConfirmationResponse from(HandoverConfirmation confirmation) {
            return new ConfirmationResponse(
                    confirmation.id(),
                    confirmation.actorUserId(),
                    confirmation.party(),
                    confirmation.observedAt(),
                    confirmation.receivedAt(),
                    confirmation.latitude(),
                    confirmation.longitude(),
                    confirmation.distanceMetres(),
                    confirmation.capturedQuantity(),
                    confirmation.photoUrl(),
                    confirmation.quantityOutcome(),
                    confirmation.quantityNote());
        }
    }
}
