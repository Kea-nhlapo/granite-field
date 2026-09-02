package za.co.trademesh.modules.document.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.document.domain.DocumentRepository;
import za.co.trademesh.modules.document.domain.DocumentState;

@Service
public class ConfirmedDocumentReader {

    private final DocumentRepository documents;

    public ConfirmedDocumentReader(DocumentRepository documents) {
        this.documents = documents;
    }

    @Transactional(readOnly = true)
    public Optional<ConfirmedDocument> find(UUID businessId, UUID documentId) {
        return documents.loadView(documentId, businessId).flatMap(view -> {
            if (view.document().state() != DocumentState.CONFIRMED
                    || view.latestConfirmation().isEmpty()) {
                return Optional.empty();
            }
            var confirmation = view.latestConfirmation().get();
            List<ConfirmedField> fields = confirmation.fields().stream()
                    .map(field -> new ConfirmedField(field.path(), field.value()))
                    .toList();
            return Optional.of(new ConfirmedDocument(documentId, confirmation.id(), confirmation.revision(), fields));
        });
    }

    public record ConfirmedDocument(
            UUID documentId, UUID confirmationId, int confirmationRevision, List<ConfirmedField> fields) {
        public ConfirmedDocument {
            fields = List.copyOf(fields);
        }
    }

    public record ConfirmedField(String path, String value) {}
}
