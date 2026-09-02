package za.co.trademesh.modules.supplier.events;

import java.util.UUID;
import za.co.trademesh.shared.events.DomainEvent;

public sealed interface SupplierEvent extends DomainEvent
        permits SupplierEvent.InvitationCreated,
                SupplierEvent.ResponseRecorded,
                SupplierEvent.InvitationRevoked,
                SupplierEvent.ProfileConverted {

    @Override
    default int schemaVersion() {
        return 1;
    }

    record InvitationCreated(UUID invitationId, UUID buyerBusinessId, UUID supplierProfileId, UUID requestId)
            implements SupplierEvent {
        @Override
        public String type() {
            return "supplier.invitation-created";
        }
    }

    record ResponseRecorded(UUID invitationId, UUID supplierProfileId, UUID requestId, UUID responseReference)
            implements SupplierEvent {
        @Override
        public String type() {
            return "supplier.invitation-response-recorded";
        }
    }

    record InvitationRevoked(UUID invitationId, UUID buyerBusinessId) implements SupplierEvent {
        @Override
        public String type() {
            return "supplier.invitation-revoked";
        }
    }

    record ProfileConverted(UUID supplierProfileId, UUID userId, UUID businessId) implements SupplierEvent {
        @Override
        public String type() {
            return "supplier.profile-converted";
        }
    }
}
