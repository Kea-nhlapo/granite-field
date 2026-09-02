package za.co.trademesh.modules.document.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.document.domain.DocumentExtraction;
import za.co.trademesh.modules.document.domain.DocumentRecord;
import za.co.trademesh.modules.document.domain.DocumentRepository;
import za.co.trademesh.modules.document.domain.DocumentState;

@Service
public class DocumentProcessingTransactions {

    private static final String PROCESSOR = "system:document-extractor";

    private final DocumentRepository documents;
    private final DocumentProcessingProperties properties;
    private final Clock clock;

    public DocumentProcessingTransactions(
            DocumentRepository documents, DocumentProcessingProperties properties, Clock clock) {
        this.documents = documents;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ProcessingClaim claim(UUID documentId) {
        DocumentRecord document = documents.findById(documentId).orElseThrow(DocumentException::notFound);
        if (document.state() == DocumentState.PARSED || document.state() == DocumentState.CONFIRMED) {
            return ProcessingClaim.completed(document);
        }

        Instant now = clock.instant();
        UUID token = UUID.randomUUID();
        if (!documents.claimProcessing(documentId, token, now, now.minus(properties.claimTimeout()))) {
            throw DocumentException.processingNotReady();
        }
        documents.addTransition(
                documentId, document.state(), DocumentState.PROCESSING, "extraction claimed", PROCESSOR, now);
        DocumentRecord claimed = documents.findById(documentId).orElseThrow(DocumentException::notFound);
        return ProcessingClaim.active(claimed, token);
    }

    @Transactional
    public boolean complete(UUID documentId, UUID token, DocumentExtraction extraction) {
        Instant now = clock.instant();
        if (!documents.completeExtraction(documentId, token, extraction, now)) {
            return false;
        }
        documents.addTransition(
                documentId, DocumentState.PROCESSING, DocumentState.PARSED, "extraction completed", PROCESSOR, now);
        return true;
    }

    @Transactional
    public void fail(UUID documentId, UUID token, Throwable failure) {
        Instant now = clock.instant();
        String error = safeError(failure);
        if (documents.markFailed(documentId, token, error, now)) {
            documents.addTransition(documentId, DocumentState.PROCESSING, DocumentState.FAILED, error, PROCESSOR, now);
        }
    }

    private static String safeError(Throwable failure) {
        return "Extraction failed (" + failure.getClass().getSimpleName() + ")";
    }

    public record ProcessingClaim(DocumentRecord document, UUID token, boolean completed) {
        static ProcessingClaim active(DocumentRecord document, UUID token) {
            return new ProcessingClaim(document, token, false);
        }

        static ProcessingClaim completed(DocumentRecord document) {
            return new ProcessingClaim(document, null, true);
        }
    }
}
