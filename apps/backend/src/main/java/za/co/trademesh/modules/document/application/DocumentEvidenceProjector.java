package za.co.trademesh.modules.document.application;

import java.util.List;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.document.domain.DocumentRecord;
import za.co.trademesh.modules.document.domain.DocumentRepository;
import za.co.trademesh.modules.document.events.DocumentEvent;
import za.co.trademesh.modules.evidence.application.EvidenceFile;
import za.co.trademesh.modules.evidence.application.EvidenceMetadata;
import za.co.trademesh.modules.evidence.application.EvidenceProjection;
import za.co.trademesh.modules.evidence.application.EvidenceProjector;
import za.co.trademesh.shared.events.DomainEvent;
import za.co.trademesh.shared.storage.FileStorageService;
import za.co.trademesh.shared.storage.StoredFile;

@Component
class DocumentEvidenceProjector implements EvidenceProjector {

    private final DocumentRepository documents;
    private final FileStorageService storage;

    DocumentEvidenceProjector(DocumentRepository documents, FileStorageService storage) {
        this.documents = documents;
        this.storage = storage;
    }

    @Override
    public boolean supports(DomainEvent event) {
        return event instanceof DocumentEvent;
    }

    @Override
    public EvidenceProjection project(DomainEvent event) {
        return switch ((DocumentEvent) event) {
            case DocumentEvent.Confirmed confirmed -> confirmed(confirmed);
            case DocumentEvent.ComparisonCompleted completed ->
                new EvidenceProjection(
                        "DOCUMENT_COMPARISON",
                        completed.comparisonId(),
                        null,
                        EvidenceMetadata.of(
                                "businessId", completed.businessId(), "indicatorCount", completed.indicatorCount()));
        };
    }

    private EvidenceProjection confirmed(DocumentEvent.Confirmed event) {
        DocumentRecord document = documents
                .findById(event.documentId())
                .orElseThrow(() -> new IllegalStateException("A confirmed document must still exist"));
        StoredFile file = storage.getMetadata(event.businessId(), document.storedFileId());
        return new EvidenceProjection(
                "DOCUMENT",
                event.documentId(),
                null,
                EvidenceMetadata.of(
                        "businessId",
                        event.businessId(),
                        "confirmationId",
                        event.confirmationId(),
                        "revision",
                        event.revision()),
                List.of(new EvidenceFile(file.id(), file.sha256())));
    }
}
