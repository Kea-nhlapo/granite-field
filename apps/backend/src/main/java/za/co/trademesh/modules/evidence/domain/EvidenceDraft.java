package za.co.trademesh.modules.evidence.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EvidenceDraft(
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
        Instant recordedAt) {}
