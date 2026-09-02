package za.co.trademesh.modules.document.api;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.document.application.DocumentComparisonService;
import za.co.trademesh.modules.document.domain.DocumentComparison;
import za.co.trademesh.modules.document.domain.DocumentComparisonSource;
import za.co.trademesh.modules.document.domain.DocumentMismatchIndicator;
import za.co.trademesh.modules.document.domain.DocumentMismatchRule;
import za.co.trademesh.modules.document.domain.DocumentMismatchSeverity;
import za.co.trademesh.modules.document.domain.DocumentType;

public final class DocumentComparisonContracts {

    private DocumentComparisonContracts() {}

    public record CompareDocumentsRequest(
            @NotNull UUID requestId,
            @NotNull UUID referenceDocumentId,
            @NotNull UUID comparedDocumentId) {

        DocumentComparisonService.CompareDocuments toCommand() {
            return new DocumentComparisonService.CompareDocuments(requestId, referenceDocumentId, comparedDocumentId);
        }
    }

    public record ComparisonResponse(
            UUID comparisonId,
            UUID businessId,
            String ruleSetVersion,
            SourceResponse reference,
            SourceResponse compared,
            List<MismatchResponse> mismatches,
            UUID createdByUserId,
            Instant createdAt) {

        static ComparisonResponse from(DocumentComparison comparison) {
            return new ComparisonResponse(
                    comparison.id(),
                    comparison.businessId(),
                    comparison.ruleSetVersion(),
                    SourceResponse.from(comparison.reference()),
                    SourceResponse.from(comparison.compared()),
                    comparison.indicators().stream().map(MismatchResponse::from).toList(),
                    comparison.createdByUserId(),
                    comparison.createdAt());
        }
    }

    public record SourceResponse(
            UUID documentId, DocumentType documentType, UUID confirmationId, int confirmationRevision) {

        static SourceResponse from(DocumentComparisonSource source) {
            return new SourceResponse(
                    source.documentId(), source.documentType(), source.confirmationId(), source.confirmationRevision());
        }
    }

    public record MismatchResponse(
            UUID indicatorId,
            DocumentMismatchRule rule,
            int ruleVersion,
            String fieldPath,
            DocumentMismatchSeverity severity,
            ComparedValueResponse reference,
            ComparedValueResponse compared,
            String explanation,
            Instant createdAt) {

        static MismatchResponse from(DocumentMismatchIndicator mismatch) {
            return new MismatchResponse(
                    mismatch.id(),
                    mismatch.rule(),
                    mismatch.ruleVersion(),
                    mismatch.fieldPath(),
                    mismatch.severity(),
                    new ComparedValueResponse(mismatch.referenceDocumentId(), mismatch.referenceValue()),
                    new ComparedValueResponse(mismatch.comparedDocumentId(), mismatch.comparedValue()),
                    mismatch.explanation(),
                    mismatch.createdAt());
        }
    }

    public record ComparedValueResponse(UUID documentId, String confirmedValue) {}
}
