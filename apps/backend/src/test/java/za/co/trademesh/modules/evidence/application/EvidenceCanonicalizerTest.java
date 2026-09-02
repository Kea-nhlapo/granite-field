package za.co.trademesh.modules.evidence.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import za.co.trademesh.modules.evidence.domain.EvidenceDraft;
import za.co.trademesh.modules.evidence.domain.EvidenceFileReference;

class EvidenceCanonicalizerTest {

    private final EvidenceCanonicalizer canonicalizer =
            new EvidenceCanonicalizer(JsonMapper.builder().build());

    @Test
    void producesTheSameChecksumForEquivalentMetadataAndFileOrdering() {
        UUID firstFile = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondFile = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Map<String, String> firstMetadata = new LinkedHashMap<>();
        firstMetadata.put("zeta", "last");
        firstMetadata.put("alpha", "first");
        Map<String, String> secondMetadata = new LinkedHashMap<>();
        secondMetadata.put("alpha", "first");
        secondMetadata.put("zeta", "last");

        EvidenceDraft first = draft(
                firstMetadata,
                List.of(
                        new EvidenceFileReference(secondFile, "b".repeat(64)),
                        new EvidenceFileReference(firstFile, "a".repeat(64))));
        EvidenceDraft second = draft(
                secondMetadata,
                List.of(
                        new EvidenceFileReference(firstFile, "a".repeat(64)),
                        new EvidenceFileReference(secondFile, "b".repeat(64))));

        assertThat(canonicalizer.checksum(first)).isEqualTo(canonicalizer.checksum(second));
    }

    @Test
    void checksumChangesWhenAnEvidenceFactChanges() {
        EvidenceDraft original = draft(Map.of("status", "COLLECTED"), List.of());
        EvidenceDraft changed = draft(Map.of("status", "DELIVERED"), List.of());

        assertThat(canonicalizer.checksum(original)).isNotEqualTo(canonicalizer.checksum(changed));
    }

    private static EvidenceDraft draft(Map<String, String> metadata, List<EvidenceFileReference> files) {
        return new EvidenceDraft(
                42,
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("10000000-0000-0000-0000-000000000002"),
                "SHIPMENT_STATUS_CHANGED",
                "SHIPMENT",
                UUID.fromString("10000000-0000-0000-0000-000000000003"),
                UUID.fromString("10000000-0000-0000-0000-000000000003"),
                Instant.parse("2026-09-02T08:00:00Z"),
                "10000000-0000-0000-0000-000000000004",
                "trademesh-backend",
                UUID.fromString("10000000-0000-0000-0000-000000000005"),
                1,
                null,
                metadata,
                files,
                Instant.parse("2026-09-02T08:00:01Z"));
    }
}
