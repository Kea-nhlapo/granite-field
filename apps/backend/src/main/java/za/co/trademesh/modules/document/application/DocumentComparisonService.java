package za.co.trademesh.modules.document.application;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.document.domain.DocumentComparison;
import za.co.trademesh.modules.document.domain.DocumentComparisonRepository;
import za.co.trademesh.modules.document.domain.DocumentComparisonSource;
import za.co.trademesh.modules.document.domain.DocumentMismatchIndicator;
import za.co.trademesh.modules.document.domain.DocumentRepository;
import za.co.trademesh.modules.document.domain.DocumentState;
import za.co.trademesh.modules.document.domain.DocumentType;
import za.co.trademesh.modules.document.domain.DocumentView;
import za.co.trademesh.modules.document.events.DocumentEvent;
import za.co.trademesh.shared.events.DomainEvents;
import za.co.trademesh.shared.storage.StoredFileRepository;

@Service
public class DocumentComparisonService {

    private static final Set<DocumentType> SUPPORTED_TYPES = EnumSet.of(
            DocumentType.PURCHASE_ORDER, DocumentType.QUOTE, DocumentType.INVOICE, DocumentType.DELIVERY_NOTE);

    private final DocumentRepository documents;
    private final DocumentComparisonRepository comparisons;
    private final StoredFileRepository files;
    private final DocumentComparisonRules rules;
    private final DomainEvents events;
    private final Clock clock;

    public DocumentComparisonService(
            DocumentRepository documents,
            DocumentComparisonRepository comparisons,
            StoredFileRepository files,
            DocumentComparisonRules rules,
            DomainEvents events,
            Clock clock) {
        this.documents = documents;
        this.comparisons = comparisons;
        this.files = files;
        this.rules = rules;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public DocumentComparison compare(UUID businessId, CompareDocuments command, UUID actorUserId) {
        if (command == null
                || command.requestId() == null
                || command.referenceDocumentId() == null
                || command.comparedDocumentId() == null
                || command.referenceDocumentId().equals(command.comparedDocumentId())) {
            throw DocumentComparisonException.invalidRequest();
        }

        Source reference = source(businessId, command.referenceDocumentId());
        Source compared = source(businessId, command.comparedDocumentId());
        var existingRequest = comparisons.findByClientRequestId(businessId, command.requestId());
        if (existingRequest.isPresent()) {
            return sameSources(existingRequest.get(), reference, compared);
        }
        var existingScope = comparisons.findByScope(
                businessId,
                reference.source().confirmationId(),
                compared.source().confirmationId(),
                DocumentComparisonRules.RULE_SET_VERSION);
        if (existingScope.isPresent()) {
            return existingScope.get();
        }

        Instant now = clock.instant();
        List<DocumentMismatchIndicator> indicators = rules.evaluate(reference.snapshot(), compared.snapshot()).stream()
                .map(proposed -> new DocumentMismatchIndicator(
                        UUID.randomUUID(),
                        proposed.rule(),
                        proposed.ruleVersion(),
                        proposed.fieldPath(),
                        proposed.severity(),
                        reference.source().documentId(),
                        proposed.referenceValue(),
                        compared.source().documentId(),
                        proposed.comparedValue(),
                        proposed.explanation(),
                        now))
                .toList();
        DocumentComparison comparison = new DocumentComparison(
                UUID.randomUUID(),
                businessId,
                DocumentComparisonRules.RULE_SET_VERSION,
                reference.source(),
                compared.source(),
                indicators,
                actorUserId,
                now);
        if (!comparisons.save(comparison, command.requestId())) {
            var concurrentRequest = comparisons.findByClientRequestId(businessId, command.requestId());
            if (concurrentRequest.isPresent()) {
                return sameSources(concurrentRequest.get(), reference, compared);
            }
            return comparisons
                    .findByScope(
                            businessId,
                            reference.source().confirmationId(),
                            compared.source().confirmationId(),
                            DocumentComparisonRules.RULE_SET_VERSION)
                    .orElseThrow(DocumentComparisonException::idempotencyConflict);
        }
        events.publish(
                new DocumentEvent.ComparisonCompleted(comparison.id(), businessId, indicators.size()),
                actorUserId.toString());
        return comparison;
    }

    @Transactional(readOnly = true)
    public DocumentComparison get(UUID businessId, UUID comparisonId) {
        return comparisons.findById(businessId, comparisonId).orElseThrow(DocumentComparisonException::notFound);
    }

    private Source source(UUID businessId, UUID documentId) {
        DocumentView view = documents
                .loadView(documentId, businessId)
                .filter(value -> value.document().state() == DocumentState.CONFIRMED)
                .filter(value -> value.latestConfirmation().isPresent())
                .orElseThrow(DocumentComparisonException::sourceUnavailable);
        if (!SUPPORTED_TYPES.contains(view.document().type())) {
            throw DocumentComparisonException.unsupportedSource();
        }
        var confirmation = view.latestConfirmation().orElseThrow();
        var storedFile = files.findByIdAndBusinessId(view.document().storedFileId(), businessId)
                .orElseThrow(DocumentComparisonException::sourceUnavailable);
        return new Source(
                new DocumentComparisonSource(
                        documentId, view.document().type(), confirmation.id(), confirmation.revision()),
                new DocumentComparisonRules.SourceSnapshot(storedFile.sha256(), confirmation.fields()));
    }

    private static DocumentComparison sameSources(DocumentComparison existing, Source reference, Source compared) {
        boolean same = existing.reference()
                        .documentId()
                        .equals(reference.source().documentId())
                && existing.reference()
                        .confirmationId()
                        .equals(reference.source().confirmationId())
                && existing.compared().documentId().equals(compared.source().documentId())
                && existing.compared().confirmationId().equals(compared.source().confirmationId())
                && existing.ruleSetVersion().equals(DocumentComparisonRules.RULE_SET_VERSION);
        if (!same) {
            throw DocumentComparisonException.idempotencyConflict();
        }
        return existing;
    }

    public record CompareDocuments(UUID requestId, UUID referenceDocumentId, UUID comparedDocumentId) {}

    private record Source(DocumentComparisonSource source, DocumentComparisonRules.SourceSnapshot snapshot) {}
}
