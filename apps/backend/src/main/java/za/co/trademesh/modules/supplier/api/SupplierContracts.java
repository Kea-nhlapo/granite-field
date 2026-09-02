package za.co.trademesh.modules.supplier.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import za.co.trademesh.modules.supplier.application.SupplierInvitationService;
import za.co.trademesh.modules.supplier.domain.SupplierInvitation;
import za.co.trademesh.modules.supplier.domain.SupplierInvitationPurpose;
import za.co.trademesh.modules.supplier.domain.SupplierInvitationStatus;
import za.co.trademesh.modules.supplier.domain.SupplierProfile;
import za.co.trademesh.modules.supplier.domain.SupplierProfileStatus;

public final class SupplierContracts {

    private SupplierContracts() {}

    public record CreateInvitationRequest(
            @NotNull UUID requestId,
            @NotBlank @Email @Size(max = 320) String supplierEmail) {}

    public record CreatedInvitationResponse(
            UUID invitationId,
            UUID supplierProfileId,
            UUID requestId,
            SupplierInvitationPurpose purpose,
            Instant expiresAt,
            String invitationToken) {
        static CreatedInvitationResponse from(SupplierInvitationService.CreatedInvitation created) {
            SupplierInvitation invitation = created.invitation();
            return new CreatedInvitationResponse(
                    invitation.id(),
                    created.profile().id(),
                    invitation.requestId(),
                    invitation.purpose(),
                    invitation.expiresAt(),
                    created.rawToken());
        }
    }

    public record GuestInvitationResponse(
            UUID invitationId,
            UUID supplierProfileId,
            UUID buyerBusinessId,
            UUID requestId,
            SupplierInvitationPurpose purpose,
            Instant expiresAt) {
        static GuestInvitationResponse from(SupplierInvitationService.GuestInvitation guest) {
            SupplierInvitation invitation = guest.invitation();
            return new GuestInvitationResponse(
                    invitation.id(),
                    invitation.supplierProfileId(),
                    invitation.buyerBusinessId(),
                    invitation.requestId(),
                    invitation.purpose(),
                    invitation.expiresAt());
        }
    }

    public record SubmitResponseRequest(
            @NotNull UUID requestId, @NotNull UUID responseReference) {}

    public record InvitationResponse(
            UUID invitationId,
            UUID requestId,
            UUID responseReference,
            SupplierInvitationStatus status,
            Instant respondedAt) {
        static InvitationResponse from(SupplierInvitation invitation) {
            return new InvitationResponse(
                    invitation.id(),
                    invitation.requestId(),
                    invitation.responseReference(),
                    invitation.status(),
                    invitation.respondedAt());
        }
    }

    public record ConvertSupplierRequest(
            @NotBlank @Size(max = 512) String invitationToken, UUID businessId) {}

    public record SupplierProfileResponse(
            UUID supplierProfileId,
            String supplierEmail,
            SupplierProfileStatus status,
            UUID claimedUserId,
            UUID businessId,
            Instant createdAt,
            Instant convertedAt) {
        static SupplierProfileResponse from(SupplierProfile profile) {
            return new SupplierProfileResponse(
                    profile.id(),
                    profile.email().value(),
                    profile.status(),
                    profile.claimedUserId(),
                    profile.businessId(),
                    profile.createdAt(),
                    profile.convertedAt());
        }
    }
}
