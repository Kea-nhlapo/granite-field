package za.co.trademesh.modules.aggregation.domain;

import java.util.Optional;
import java.util.UUID;

public interface DemandGroupSuggestionRepository {

    boolean save(DemandGroupSuggestion suggestion, UUID clientRequestId);

    Optional<DemandGroupSuggestion> findById(UUID businessId, UUID suggestionId);

    Optional<DemandGroupSuggestion> findByClientRequestId(UUID businessId, UUID clientRequestId);

    Optional<DemandGroupSuggestion> findActiveByFingerprint(UUID businessId, String inputFingerprint);
}
