package za.co.trademesh.modules.document.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentExtraction(
        UUID id,
        UUID documentId,
        String providerName,
        String parserVersion,
        String rawResultReference,
        List<ExtractedDocumentField> fields,
        Instant completedAt) {

    public DocumentExtraction {
        fields = List.copyOf(fields);
    }
}
