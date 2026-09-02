package za.co.trademesh.modules.evidence.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Internal read boundary used by authorized views such as insurance. */
public interface ShipmentEvidenceCatalog {

    ShipmentEvidencePackage packageFor(UUID shipmentId);

    record ShipmentEvidencePackage(UUID shipmentId, List<Entry> entries) {}

    record Entry(
            long sequence,
            UUID evidenceId,
            UUID eventId,
            String type,
            String subjectType,
            UUID subjectId,
            Instant occurredAt,
            String actor,
            String source,
            UUID correlationId,
            int schemaVersion,
            UUID correctionOfId,
            Map<String, String> metadata,
            List<FileReference> files,
            String payloadChecksum,
            Integrity integrity) {}

    record FileReference(UUID fileId, String sha256) {}

    enum Integrity {
        VERIFIED,
        CHECKSUM_MISMATCH
    }
}
