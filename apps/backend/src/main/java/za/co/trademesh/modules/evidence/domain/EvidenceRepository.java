package za.co.trademesh.modules.evidence.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvidenceRepository {

    long nextSequence();

    AppendResult append(EvidenceDraft draft, String payloadChecksum, Optional<EvidenceChainLink> chainLink);

    Optional<EvidenceRecord> findById(UUID evidenceId);

    Optional<EvidenceRecord> findByEventId(UUID eventId);

    List<EvidenceRecord> findByShipmentId(UUID shipmentId);

    record AppendResult(EvidenceRecord record, boolean created) {}
}
