package za.co.trademesh.modules.delivery.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.delivery.domain.DeliveryProposalRepository;
import za.co.trademesh.modules.delivery.domain.DeliveryProposalStatus;

@Service
class AcceptedDeliveryService implements AcceptedDeliveryCatalog {

    private final DeliveryProposalRepository proposals;

    AcceptedDeliveryService(DeliveryProposalRepository proposals) {
        this.proposals = proposals;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AcceptedDelivery> find(UUID proposalId) {
        return proposals
                .findById(proposalId)
                .filter(proposal -> proposal.status() == DeliveryProposalStatus.ACCEPTED)
                .map(proposal -> new AcceptedDelivery(
                        proposal.id(), proposal.shipmentId(), proposal.businessId(), proposal.recipientPhone()));
    }
}
