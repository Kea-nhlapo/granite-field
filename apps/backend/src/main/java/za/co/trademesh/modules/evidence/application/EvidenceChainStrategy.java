package za.co.trademesh.modules.evidence.application;

import java.util.Optional;
import java.util.UUID;
import za.co.trademesh.modules.evidence.domain.EvidenceChainLink;

/** Optional extension point. The first release deliberately does not hash-chain records. */
public interface EvidenceChainStrategy {

    Optional<EvidenceChainLink> link(UUID shipmentId, String payloadChecksum);
}
