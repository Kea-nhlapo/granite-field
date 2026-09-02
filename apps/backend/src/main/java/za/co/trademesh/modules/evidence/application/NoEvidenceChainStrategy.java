package za.co.trademesh.modules.evidence.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.evidence.domain.EvidenceChainLink;

@Component
class NoEvidenceChainStrategy implements EvidenceChainStrategy {

    @Override
    public Optional<EvidenceChainLink> link(UUID shipmentId, String payloadChecksum) {
        return Optional.empty();
    }
}
