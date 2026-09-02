package za.co.trademesh.modules.delivery.events;

import java.util.UUID;
import za.co.trademesh.shared.events.DomainEvent;

public sealed interface DeliveryEvent extends DomainEvent
        permits DeliveryEvent.ProposalCreated, DeliveryEvent.DeliveryAccepted {

    @Override
    default int schemaVersion() {
        return 1;
    }

    record ProposalCreated(UUID proposalId, UUID shipmentId, UUID businessId) implements DeliveryEvent {
        @Override
        public String type() {
            return "DELIVERY_PROPOSED";
        }
    }

    record DeliveryAccepted(UUID proposalId, UUID shipmentId, UUID businessId) implements DeliveryEvent {
        @Override
        public String type() {
            return "DELIVERY_ACCEPTED";
        }
    }
}
