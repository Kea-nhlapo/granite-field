package za.co.trademesh.modules.evidence.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EvidenceRecord(
        long ledgerSequence,
        UUID id,
        UUID eventId,
        String type,
        String subjectType,
        UUID subjectId,
        UUID shipmentId,
        Instant occurredAt,
        String actor,
        String source,
        UUID correlationId,
        int schemaVersion,
        UUID correctionOfId,
        Map<String, String> metadata,
        List<EvidenceFileReference> files,
        String payloadChecksum,
        String previousChainHash,
        String chainHash,
        Instant recordedAt) {

    public EvidenceDraft asDraft() {
        return new EvidenceDraft(
                ledgerSequence,
                id,
                eventId,
                type,
                subjectType,
                subjectId,
                shipmentId,
                occurredAt,
                actor,
                source,
                correlationId,
                schemaVersion,
                correctionOfId,
                metadata,
                files,
                recordedAt);
    }
}
