package za.co.trademesh.modules.document.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.document.domain.ConfirmedDocumentField;
import za.co.trademesh.modules.document.domain.DocumentRecord;
import za.co.trademesh.modules.document.domain.DocumentRepository;
import za.co.trademesh.modules.document.domain.DocumentState;
import za.co.trademesh.modules.document.domain.DocumentType;
import za.co.trademesh.modules.document.domain.DocumentView;
import za.co.trademesh.modules.document.events.DocumentEvent;
import za.co.trademesh.shared.events.DomainEvents;
import za.co.trademesh.shared.events.outbox.OutboxSubmitter;
import za.co.trademesh.shared.storage.FileScanStatus;
import za.co.trademesh.shared.storage.FileStorageService;
import za.co.trademesh.shared.storage.FileStorageStatus;
import za.co.trademesh.shared.storage.StorageException;
import za.co.trademesh.shared.storage.StoredFile;

@Service
public class DocumentService {

    private static final int MAX_CONFIRMATION_FIELDS = 500;
    private static final int MAX_FIELD_PATH = 255;
    private static final int MAX_FIELD_VALUE = 4000;

    private final DocumentRepository documents;
    private final FileStorageService storage;
    private final OutboxSubmitter outbox;
    private final DomainEvents events;
    private final Clock clock;

    public DocumentService(
            DocumentRepository documents,
            FileStorageService storage,
            OutboxSubmitter outbox,
            DomainEvents events,
            Clock clock) {
        this.documents = documents;
        this.storage = storage;
        this.outbox = outbox;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public DocumentView register(
            UUID businessId, UUID storedFileId, DocumentType type, UUID clientRequestId, UUID actorUserId) {
        var source = sourceFile(businessId, storedFileId);
        if (source.storageStatus() != FileStorageStatus.AVAILABLE || source.scanStatus() != FileScanStatus.CLEAN) {
            throw DocumentException.sourceFileUnavailable();
        }

        var existingRequest = documents.findByBusinessIdAndRequestId(businessId, clientRequestId);
        if (existingRequest.isPresent()) {
            return sameRegistration(existingRequest.get(), storedFileId, type, businessId);
        }
        if (documents.findByStoredFileId(storedFileId).isPresent()) {
            throw DocumentException.sourceAlreadyRegistered();
        }

        Instant now = clock.instant();
        UUID documentId = UUID.randomUUID();
        DocumentRecord uploaded = new DocumentRecord(
                documentId, businessId, storedFileId, type, DocumentState.UPLOADED, 0, null, actorUserId, now, now);
        if (!documents.save(uploaded, clientRequestId)) {
            var concurrent = documents.findByBusinessIdAndRequestId(businessId, clientRequestId);
            if (concurrent.isPresent()) {
                return sameRegistration(concurrent.get(), storedFileId, type, businessId);
            }
            throw DocumentException.sourceAlreadyRegistered();
        }
        String actor = actorUserId.toString();
        documents.addTransition(documentId, null, DocumentState.UPLOADED, "source file registered", actor, now);
        if (!documents.moveToQueued(documentId, now)) {
            throw DocumentException.processingNotReady();
        }
        documents.addTransition(
                documentId, DocumentState.UPLOADED, DocumentState.QUEUED, "extraction queued", actor, now);
        outbox.submit(
                DocumentExtractionRequested.TYPE,
                documentId.toString(),
                new DocumentExtractionRequested(documentId),
                DocumentExtractionRequested.SCHEMA_VERSION);
        return view(documentId, businessId);
    }

    @Transactional(readOnly = true)
    public DocumentView get(UUID businessId, UUID documentId) {
        return view(documentId, businessId);
    }

    @Transactional
    public DocumentView confirm(
            UUID businessId,
            UUID documentId,
            UUID requestId,
            List<ConfirmedDocumentField> requestedFields,
            UUID actorUserId) {
        List<ConfirmedDocumentField> fields = normalizeFields(requestedFields);
        DocumentRecord document =
                documents.findByIdAndBusinessId(documentId, businessId).orElseThrow(DocumentException::notFound);
        if (document.state() != DocumentState.PARSED && document.state() != DocumentState.CONFIRMED) {
            throw DocumentException.notReadyForConfirmation();
        }

        DocumentRepository.ConfirmationWrite write = documents
                .addConfirmation(documentId, requestId, actorUserId, fields, clock.instant())
                .orElseThrow(DocumentException::notReadyForConfirmation);
        if (!write.created() && !write.confirmation().fields().equals(fields)) {
            throw DocumentException.requestConflict();
        }
        if (write.transitionedToConfirmed()) {
            documents.addTransition(
                    documentId,
                    DocumentState.PARSED,
                    DocumentState.CONFIRMED,
                    "fields confirmed",
                    actorUserId.toString(),
                    write.confirmation().createdAt());
        }
        if (write.created()) {
            events.publish(
                    new DocumentEvent.Confirmed(
                            documentId,
                            businessId,
                            write.confirmation().id(),
                            write.confirmation().revision()),
                    actorUserId.toString());
        }
        return view(documentId, businessId);
    }

    private DocumentView sameRegistration(
            DocumentRecord existing, UUID storedFileId, DocumentType type, UUID businessId) {
        if (!existing.storedFileId().equals(storedFileId) || existing.type() != type) {
            throw DocumentException.requestConflict();
        }
        return view(existing.id(), businessId);
    }

    private StoredFile sourceFile(UUID businessId, UUID storedFileId) {
        try {
            return storage.getMetadata(businessId, storedFileId);
        } catch (StorageException missing) {
            if ("FILE_NOT_FOUND".equals(missing.code())) {
                throw DocumentException.sourceFileNotFound();
            }
            throw missing;
        }
    }

    private DocumentView view(UUID documentId, UUID businessId) {
        return documents.loadView(documentId, businessId).orElseThrow(DocumentException::notFound);
    }

    private static List<ConfirmedDocumentField> normalizeFields(List<ConfirmedDocumentField> requestedFields) {
        if (requestedFields == null || requestedFields.isEmpty() || requestedFields.size() > MAX_CONFIRMATION_FIELDS) {
            throw DocumentException.invalidConfirmation();
        }
        List<ConfirmedDocumentField> normalized = new ArrayList<>(requestedFields.size());
        HashSet<String> paths = new HashSet<>();
        for (ConfirmedDocumentField field : requestedFields) {
            if (field == null || field.path() == null || field.value() == null) {
                throw DocumentException.invalidConfirmation();
            }
            String path = field.path().strip();
            String value = field.value().strip();
            if (path.isEmpty()
                    || path.length() > MAX_FIELD_PATH
                    || value.isEmpty()
                    || value.length() > MAX_FIELD_VALUE
                    || !paths.add(path)) {
                throw DocumentException.invalidConfirmation();
            }
            normalized.add(new ConfirmedDocumentField(path, value));
        }
        normalized.sort(Comparator.comparing(ConfirmedDocumentField::path));
        return List.copyOf(normalized);
    }
}
