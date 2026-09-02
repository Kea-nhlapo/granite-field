package za.co.trademesh.modules.document.domain;

import java.util.Optional;
import java.util.UUID;

public interface DocumentComparisonRepository {

    boolean save(DocumentComparison comparison, UUID clientRequestId);

    Optional<DocumentComparison> findById(UUID businessId, UUID comparisonId);

    Optional<DocumentComparison> findByClientRequestId(UUID businessId, UUID clientRequestId);

    Optional<DocumentComparison> findByScope(
            UUID businessId, UUID referenceConfirmationId, UUID comparedConfirmationId, String ruleSetVersion);
}
