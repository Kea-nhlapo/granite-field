package za.co.trademesh.modules.document.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.document.domain.ConfirmedDocumentField;
import za.co.trademesh.modules.document.domain.DocumentConfirmation;
import za.co.trademesh.modules.document.domain.DocumentExtraction;
import za.co.trademesh.modules.document.domain.DocumentState;
import za.co.trademesh.modules.document.domain.DocumentStateTransition;
import za.co.trademesh.modules.document.domain.DocumentType;
import za.co.trademesh.modules.document.domain.DocumentView;
import za.co.trademesh.modules.document.domain.ExtractedDocumentField;

public final class DocumentContracts {

    private DocumentContracts() {}

    public record RegisterDocumentRequest(
            @NotNull UUID storedFileId,
            @NotNull DocumentType type,
            @NotNull UUID requestId) {}

    public record ConfirmDocumentRequest(
            @NotNull UUID requestId,
            @NotEmpty @Size(max = 500) List<@Valid ConfirmationFieldRequest> fields) {}

    public record ConfirmationFieldRequest(
            @NotBlank @Size(max = 255) String path,
            @NotBlank @Size(max = 4000) String value) {
        ConfirmedDocumentField toDomain() {
            return new ConfirmedDocumentField(path, value);
        }
    }

    public record DocumentResponse(
            UUID documentId,
            UUID businessId,
            UUID storedFileId,
            DocumentType type,
            DocumentState state,
            int processingAttempts,
            UUID createdByUserId,
            Instant createdAt,
            Instant updatedAt,
            ExtractionResponse extraction,
            ConfirmationResponse confirmation,
            List<StateTransitionResponse> stateHistory) {

        static DocumentResponse from(DocumentView view) {
            var document = view.document();
            return new DocumentResponse(
                    document.id(),
                    document.businessId(),
                    document.storedFileId(),
                    document.type(),
                    document.state(),
                    document.processingAttempts(),
                    document.createdByUserId(),
                    document.createdAt(),
                    document.updatedAt(),
                    view.extraction().map(ExtractionResponse::from).orElse(null),
                    view.latestConfirmation().map(ConfirmationResponse::from).orElse(null),
                    view.stateHistory().stream()
                            .map(StateTransitionResponse::from)
                            .toList());
        }
    }

    public record ExtractionResponse(
            UUID extractionId,
            String provider,
            String parserVersion,
            String rawResultReference,
            List<ExtractedFieldResponse> fields,
            Instant completedAt) {
        static ExtractionResponse from(DocumentExtraction extraction) {
            return new ExtractionResponse(
                    extraction.id(),
                    extraction.providerName(),
                    extraction.parserVersion(),
                    extraction.rawResultReference(),
                    extraction.fields().stream()
                            .map(ExtractedFieldResponse::from)
                            .toList(),
                    extraction.completedAt());
        }
    }

    public record ExtractedFieldResponse(
            String path, String value, BigDecimal confidence, Integer sourcePage, String sourceRegion) {
        static ExtractedFieldResponse from(ExtractedDocumentField field) {
            return new ExtractedFieldResponse(
                    field.path(), field.value(), field.confidence(), field.sourcePage(), field.sourceRegion());
        }
    }

    public record ConfirmationResponse(
            UUID confirmationId,
            UUID requestId,
            int revision,
            UUID confirmedByUserId,
            List<ConfirmationFieldResponse> fields,
            Instant createdAt) {
        static ConfirmationResponse from(DocumentConfirmation confirmation) {
            return new ConfirmationResponse(
                    confirmation.id(),
                    confirmation.requestId(),
                    confirmation.revision(),
                    confirmation.confirmedByUserId(),
                    confirmation.fields().stream()
                            .map(field -> new ConfirmationFieldResponse(field.path(), field.value()))
                            .toList(),
                    confirmation.createdAt());
        }
    }

    public record ConfirmationFieldResponse(String path, String value) {}

    public record StateTransitionResponse(
            DocumentState fromState, DocumentState toState, String reason, String actor, Instant occurredAt) {
        static StateTransitionResponse from(DocumentStateTransition transition) {
            return new StateTransitionResponse(
                    transition.fromState(),
                    transition.toState(),
                    transition.reason(),
                    transition.actor(),
                    transition.occurredAt());
        }
    }
}
