package za.co.trademesh.modules.delivery.domain;

import java.time.Instant;
import java.util.UUID;

public record DeliveryProposal(
        UUID id,
        UUID businessId,
        UUID shipmentId,
        UUID clientRequestId,
        String inputFingerprint,
        String recipientEmail,
        String recipientPhone,
        DeliveryMobileChannel mobileChannel,
        DeliveryProposalStatus status,
        Instant expiresAt,
        Instant createdAt,
        Instant acceptedAt) {

    public boolean isExpiredAt(Instant now) {
        return status == DeliveryProposalStatus.PROPOSED && !expiresAt.isAfter(now);
    }
}
