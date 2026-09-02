package za.co.trademesh.modules.delivery.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.delivery.application.DeliveryProposalService;
import za.co.trademesh.modules.delivery.application.VoiceSupplierSearchService;
import za.co.trademesh.modules.delivery.domain.DeliveryMobileChannel;
import za.co.trademesh.modules.delivery.domain.DeliveryProposal;
import za.co.trademesh.modules.delivery.domain.DeliveryProposalStatus;

final class DeliveryContracts {

    private DeliveryContracts() {}

    record ProposeDeliveryRequest(
            @NotNull UUID businessId,
            @NotNull UUID requestId,
            @NotBlank @Email @Size(max = 320) String recipientEmail,

            @NotBlank @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$")
            String recipientPhone,

            @NotNull DeliveryMobileChannel mobileChannel) {

        DeliveryProposalService.ProposeDelivery toCommand() {
            return new DeliveryProposalService.ProposeDelivery(
                    requestId, recipientEmail, recipientPhone, mobileChannel);
        }
    }

    record DeliveryProposalResponse(
            UUID proposalId,
            UUID shipmentId,
            DeliveryProposalStatus status,
            Instant expiresAt,
            Instant acceptedAt,
            boolean newlyCreated) {

        static DeliveryProposalResponse from(DeliveryProposalService.CreatedProposal created) {
            DeliveryProposal proposal = created.proposal();
            return new DeliveryProposalResponse(
                    proposal.id(),
                    proposal.shipmentId(),
                    proposal.status(),
                    proposal.expiresAt(),
                    proposal.acceptedAt(),
                    created.newlyCreated());
        }

        static DeliveryProposalResponse from(DeliveryProposal proposal) {
            return new DeliveryProposalResponse(
                    proposal.id(),
                    proposal.shipmentId(),
                    proposal.status(),
                    proposal.expiresAt(),
                    proposal.acceptedAt(),
                    false);
        }
    }

    record VoiceSearchResponse(String detectedLanguage, String transcript, List<SupplierResponse> suppliers) {

        static VoiceSearchResponse from(VoiceSupplierSearchService.SearchResult result) {
            return new VoiceSearchResponse(
                    result.detectedLanguage(),
                    result.transcript(),
                    result.suppliers().stream().map(SupplierResponse::from).toList());
        }
    }

    record SupplierResponse(
            UUID supplierProfileId,
            UUID businessId,
            String displayName,
            BigDecimal averageRating,
            BigDecimal successfulDeliveryRate,
            Long distanceMetres,
            Long durationSeconds,
            BigDecimal rankingScore) {

        static SupplierResponse from(VoiceSupplierSearchService.RankedSupplier supplier) {
            return new SupplierResponse(
                    supplier.supplierProfileId(),
                    supplier.businessId(),
                    supplier.displayName(),
                    supplier.averageRating(),
                    supplier.successfulDeliveryRate(),
                    supplier.distanceMetres(),
                    supplier.durationSeconds(),
                    supplier.rankingScore());
        }
    }
}
