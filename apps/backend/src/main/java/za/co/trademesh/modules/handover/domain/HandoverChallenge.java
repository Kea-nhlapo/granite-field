package za.co.trademesh.modules.handover.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HandoverChallenge(
        UUID id,
        UUID shipmentId,
        UUID businessId,
        HandoverType type,
        UUID deliveryOrderId,
        HandoverState state,
        String nonceHash,
        UUID initiatorUserId,
        UUID counterpartyUserId,
        HandoverLocation expectedLocation,
        BigDecimal expectedQuantity,
        String unitOfMeasure,
        int locationToleranceMetres,
        Instant expiresAt,
        Instant completedAt,
        UUID correlationId,
        Instant createdAt,
        List<HandoverConfirmation> confirmations) {

    public HandoverChallenge {
        confirmations = List.copyOf(confirmations);
    }

    public boolean confirmedBy(HandoverParty party) {
        return confirmations.stream().anyMatch(confirmation -> confirmation.party() == party);
    }

    public boolean hasQuantityDispute() {
        return confirmations.stream()
                .anyMatch(confirmation -> confirmation.quantityOutcome() == QuantityOutcome.DISPUTED);
    }
}
