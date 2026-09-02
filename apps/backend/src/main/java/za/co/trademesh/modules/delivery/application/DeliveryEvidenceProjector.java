package za.co.trademesh.modules.delivery.application;

import org.springframework.stereotype.Component;
import za.co.trademesh.modules.delivery.events.DeliveryEvent;
import za.co.trademesh.modules.evidence.application.EvidenceMetadata;
import za.co.trademesh.modules.evidence.application.EvidenceProjection;
import za.co.trademesh.modules.evidence.application.EvidenceProjector;
import za.co.trademesh.shared.events.DomainEvent;

@Component
class DeliveryEvidenceProjector implements EvidenceProjector {

    @Override
    public boolean supports(DomainEvent event) {
        return event instanceof DeliveryEvent;
    }

    @Override
    public EvidenceProjection project(DomainEvent event) {
        return switch ((DeliveryEvent) event) {
            case DeliveryEvent.ProposalCreated proposed ->
                new EvidenceProjection(
                        "DELIVERY_PROPOSAL",
                        proposed.proposalId(),
                        proposed.shipmentId(),
                        EvidenceMetadata.of("businessId", proposed.businessId(), "shipmentId", proposed.shipmentId()));
            case DeliveryEvent.DeliveryAccepted accepted ->
                new EvidenceProjection(
                        "DELIVERY_PROPOSAL",
                        accepted.proposalId(),
                        accepted.shipmentId(),
                        EvidenceMetadata.of("businessId", accepted.businessId(), "shipmentId", accepted.shipmentId()));
        };
    }
}
