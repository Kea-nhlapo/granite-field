package za.co.trademesh.modules.evidence.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EvidenceCorrection(
        UUID originalEvidenceId,
        UUID eventId,
        String type,
        Instant occurredAt,
        String actor,
        String source,
        UUID correlationId,
        int schemaVersion,
        Map<String, String> metadata,
        List<EvidenceFile> files) {}
