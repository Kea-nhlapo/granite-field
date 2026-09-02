package za.co.trademesh.modules.document.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {

    boolean save(DocumentRecord document, UUID clientRequestId);

    Optional<DocumentRecord> findById(UUID documentId);

    Optional<DocumentRecord> findByIdAndBusinessId(UUID documentId, UUID businessId);

    Optional<DocumentRecord> findByBusinessIdAndRequestId(UUID businessId, UUID clientRequestId);

    Optional<DocumentRecord> findByStoredFileId(UUID storedFileId);

    boolean moveToQueued(UUID documentId, Instant now);

    boolean claimProcessing(UUID documentId, UUID processingToken, Instant now, Instant staleBefore);

    boolean markFailed(UUID documentId, UUID processingToken, String error, Instant now);

    boolean completeExtraction(
            UUID documentId, UUID processingToken, DocumentExtraction extraction, Instant completedAt);

    void addTransition(
            UUID documentId,
            DocumentState fromState,
            DocumentState toState,
            String reason,
            String actor,
            Instant occurredAt);

    Optional<ConfirmationWrite> addConfirmation(
            UUID documentId, UUID requestId, UUID confirmedByUserId, List<ConfirmedDocumentField> fields, Instant now);

    Optional<DocumentView> loadView(UUID documentId, UUID businessId);

    record ConfirmationWrite(DocumentConfirmation confirmation, boolean created, boolean transitionedToConfirmed) {}
}
