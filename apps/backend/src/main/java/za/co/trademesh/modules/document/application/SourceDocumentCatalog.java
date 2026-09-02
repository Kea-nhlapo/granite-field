package za.co.trademesh.modules.document.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Sanitized document metadata for an already-authorized evidence package. */
public interface SourceDocumentCatalog {

    Optional<SourceDocument> find(UUID documentId);

    record SourceDocument(
            UUID documentId,
            String documentType,
            String documentState,
            UUID fileId,
            String fileSha256,
            String fileAvailability,
            Instant documentCreatedAt,
            Instant fileStoredAt) {}
}
