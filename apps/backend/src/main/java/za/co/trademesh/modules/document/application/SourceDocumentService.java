package za.co.trademesh.modules.document.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.document.domain.DocumentRecord;
import za.co.trademesh.modules.document.domain.DocumentRepository;
import za.co.trademesh.modules.document.domain.DocumentType;
import za.co.trademesh.shared.storage.StoredFile;
import za.co.trademesh.shared.storage.StoredFileRepository;

@Service
class SourceDocumentService implements SourceDocumentCatalog {

    private final DocumentRepository documents;
    private final StoredFileRepository files;

    SourceDocumentService(DocumentRepository documents, StoredFileRepository files) {
        this.documents = documents;
        this.files = files;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SourceDocument> find(UUID documentId) {
        return documents
                .findById(documentId)
                .filter(document -> document.type() != DocumentType.COMPANY_DOCUMENT)
                .map(this::snapshot);
    }

    private SourceDocument snapshot(DocumentRecord document) {
        Optional<StoredFile> file = files.findById(document.storedFileId());
        return new SourceDocument(
                document.id(),
                document.type().name(),
                document.state().name(),
                document.storedFileId(),
                file.map(StoredFile::sha256).orElse(null),
                file.map(stored -> stored.storageStatus().name()).orElse("MISSING"),
                document.createdAt(),
                file.map(StoredFile::storedAt).orElse(null));
    }
}
