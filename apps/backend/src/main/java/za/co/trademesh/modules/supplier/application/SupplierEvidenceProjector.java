package za.co.trademesh.modules.supplier.application;

import org.springframework.stereotype.Component;
import za.co.trademesh.modules.evidence.application.EvidenceMetadata;
import za.co.trademesh.modules.evidence.application.EvidenceProjection;
import za.co.trademesh.modules.evidence.application.EvidenceProjector;
import za.co.trademesh.modules.supplier.events.SupplierEvent;
import za.co.trademesh.shared.events.DomainEvent;

@Component
class SupplierEvidenceProjector implements EvidenceProjector {

    @Override
    public boolean supports(DomainEvent event) {
        return event instanceof SupplierEvent;
    }

    @Override
    public EvidenceProjection project(DomainEvent event) {
        return switch ((SupplierEvent) event) {
            case SupplierEvent.InvitationCreated created ->
                new EvidenceProjection(
                        "SUPPLIER_INVITATION",
                        created.invitationId(),
                        null,
                        EvidenceMetadata.of(
                                "buyerBusinessId",
                                created.buyerBusinessId(),
                                "supplierProfileId",
                                created.supplierProfileId(),
                                "requestId",
                                created.requestId()));
            case SupplierEvent.ResponseRecorded recorded ->
                new EvidenceProjection(
                        "SUPPLIER_INVITATION",
                        recorded.invitationId(),
                        null,
                        EvidenceMetadata.of(
                                "supplierProfileId",
                                recorded.supplierProfileId(),
                                "requestId",
                                recorded.requestId(),
                                "responseReference",
                                recorded.responseReference()));
            case SupplierEvent.InvitationRevoked revoked ->
                new EvidenceProjection(
                        "SUPPLIER_INVITATION",
                        revoked.invitationId(),
                        null,
                        EvidenceMetadata.of("buyerBusinessId", revoked.buyerBusinessId()));
            case SupplierEvent.ProfileConverted converted ->
                new EvidenceProjection(
                        "SUPPLIER_PROFILE",
                        converted.supplierProfileId(),
                        null,
                        EvidenceMetadata.of("userId", converted.userId(), "businessId", converted.businessId()));
        };
    }
}
