package za.co.trademesh.modules.delivery.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryProposalRepository {

    Optional<DeliveryProposal> findById(UUID proposalId);

    Optional<DeliveryProposal> findByShipment(UUID businessId, UUID shipmentId);

    Optional<DeliveryProposal> findByRequest(UUID businessId, UUID clientRequestId);

    Optional<DeliveryProposal> findByTokenHash(String tokenHash);

    boolean save(DeliveryProposal proposal, String tokenHash);

    boolean accept(UUID proposalId, String tokenHash, Instant now);

    void expire(UUID proposalId, Instant now);
}
