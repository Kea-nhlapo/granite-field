package za.co.trademesh.modules.supplier.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SupplierInvitationRepository {

    SupplierProfile getOrCreateTemporaryProfile(SupplierEmail email, UUID proposedId, Instant now);

    Optional<SupplierProfile> findProfileById(UUID profileId);

    Optional<SupplierInvitation> findInvitationById(UUID invitationId);

    Optional<SupplierInvitation> findInvitationByTokenHash(String tokenHash);

    boolean activeInvitationExists(UUID buyerBusinessId, UUID requestId, UUID supplierProfileId, Instant now);

    void expirePendingForScope(UUID buyerBusinessId, UUID requestId, UUID supplierProfileId, Instant now);

    void saveInvitation(SupplierInvitation invitation, String tokenHash);

    boolean recordResponse(
            UUID invitationId, String tokenHash, UUID requestId, UUID responseReference, Instant respondedAt);

    boolean revoke(UUID invitationId, UUID buyerBusinessId, Instant revokedAt);

    void markExpired(UUID invitationId, Instant now);

    boolean claimProfile(UUID profileId, UUID userId, UUID businessId, Instant convertedAt);
}
