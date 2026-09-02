package za.co.trademesh.modules.document.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentComparison(
        UUID id,
        UUID businessId,
        String ruleSetVersion,
        DocumentComparisonSource reference,
        DocumentComparisonSource compared,
        List<DocumentMismatchIndicator> indicators,
        UUID createdByUserId,
        Instant createdAt) {

    public DocumentComparison {
        indicators = List.copyOf(indicators);
    }
}
