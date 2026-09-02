package za.co.trademesh.modules.supplier.domain;

import java.time.Instant;
import java.util.UUID;

public record SupplierInvitation(
        UUID id,
        UUID buyerBusinessId,
        UUID supplierProfileId,
        UUID requestId,
        SupplierInvitationPurpose purpose,
        SupplierInvitationStatus status,
        Instant expiresAt,
        UUID responseReference,
        Instant createdAt,
        Instant respondedAt,
        Instant revokedAt) {

    public boolean isAvailableAt(Instant now) {
        return status == SupplierInvitationStatus.PENDING && expiresAt.isAfter(now);
    }

    public boolean supportsConversionAt(Instant now) {
        return (status == SupplierInvitationStatus.PENDING || status == SupplierInvitationStatus.RESPONDED)
                && expiresAt.isAfter(now);
    }

    public boolean isIdempotentResponse(UUID expectedRequestId, UUID submittedResponseReference) {
        return status == SupplierInvitationStatus.RESPONDED
                && requestId.equals(expectedRequestId)
                && responseReference.equals(submittedResponseReference);
    }
}
