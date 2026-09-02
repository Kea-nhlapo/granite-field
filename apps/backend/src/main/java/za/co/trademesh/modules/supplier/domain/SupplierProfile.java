package za.co.trademesh.modules.supplier.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SupplierProfile(
        UUID id,
        SupplierEmail email,
        SupplierProfileStatus status,
        UUID claimedUserId,
        UUID businessId,
        Instant createdAt,
        Instant convertedAt) {

    public SupplierProfile {
        boolean temporary = status == SupplierProfileStatus.TEMPORARY;
        if (temporary && (claimedUserId != null || businessId != null || convertedAt != null)) {
            throw new IllegalArgumentException("A temporary supplier cannot have account ownership");
        }
        if (!temporary && (claimedUserId == null || convertedAt == null)) {
            throw new IllegalArgumentException("A registered supplier must have account ownership");
        }
    }

    public boolean isClaimedBy(UUID userId, UUID claimedBusinessId) {
        return status == SupplierProfileStatus.REGISTERED
                && claimedUserId.equals(userId)
                && Objects.equals(businessId, claimedBusinessId);
    }
}
