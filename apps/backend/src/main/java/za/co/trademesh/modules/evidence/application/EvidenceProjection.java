package za.co.trademesh.modules.evidence.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EvidenceProjection(
        String subjectType, UUID subjectId, UUID shipmentId, Map<String, String> metadata, List<EvidenceFile> files) {

    public EvidenceProjection(String subjectType, UUID subjectId, UUID shipmentId, Map<String, String> metadata) {
        this(subjectType, subjectId, shipmentId, metadata, List.of());
    }
}
